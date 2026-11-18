package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.CteDeclaration
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.ValueLayout
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.appendQuotedSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.ScopeKind.ARM
import com.siimkinks.sqlitemagic.internal.sql.ScopeKind.DERIVED
import com.siimkinks.sqlitemagic.internal.sql.ScopeKind.NESTED
import com.siimkinks.sqlitemagic.internal.sql.ScopeKind.ROOT

internal interface SqlRenderable {
  fun render(context: SqlRenderingContext)
}

/**
 * Each query has its own source scope, which encloses the scopes of its nested queries, so their fields of tables
 * they do not use reference the nearest enclosing occurrence, as SQL resolves names. A derived source's body is
 * non-lateral: its scope skips the query it is a source of.
 *
 * A context rendering a view [definition] writes values as literals and rejects parameters.
 */
internal class SqlRenderingContext(
  val definition: DefinitionFacts? = null,
  private val describing: Boolean = false
) {
  val sql = StringBuilder(256)
  val bindings = mutableListOf<QueryBinding>()
  var parameters: MutableScatterSet<ParameterIdentity<*>>? = null
    private set
  val dependencies = QueryDependencies.Builder(collectDirectSources = definition != null)
  private var scope = SourceScope(
    enclosing = null,
    kind = ROOT,
    exportedNames = definition?.exportedNames.orEmpty()
  )

  private var ctes: MutableScatterMap<CteDeclaration, CteBody>? = null
  private var reservedCteNames: MutableScatterSet<String>? = null
  private var lastCteNumber = 0

  var insertingValues = false

  var upsertTarget: Table<*>? = null

  private var fromEnd = -1

  val endsWithFrom get() = fromEnd == sql.length

  fun markFromEnd() {
    fromEnd = sql.length
  }

  fun requireExcluded(table: Table<*>, name: String) {
    val target = upsertTarget ?: return
    require(table === target) { "EXCLUDED column $name must be a field of the inserted ${target.name} occurrence" }
  }

  val nested get() = scope.kind == NESTED

  val root get() = scope.kind == ROOT

  var relatedEntities: RelatedEntities?
    get() = scope.relatedEntities
    set(value) {
      scope.relatedEntities = value
    }

  var valueLayout: ValueLayout?
    get() = scope.valueLayout
    set(value) {
      scope.valueLayout = value
    }

  fun renderNested(render: () -> Unit) = inScope(
    next = SourceScope(
      enclosing = scope,
      kind = NESTED
    ),
    render = render
  )

  fun renderArm(render: () -> Unit) = inScope(
    next = SourceScope(
      enclosing = scope.enclosing,
      kind = when (scope.kind) {
        NESTED -> NESTED
        else -> ARM
      }
    ),
    render = render
  )

  /** The selection that deferred its outputs reads the derived source that renders next, the query's FROM. */
  fun renderDerived(occurrence: Table<*>, body: SelectNode<*, *>) {
    val current = scope
    val index = current.pendingDerivedOutputs
    val reader = renderBody(
      body = body,
      read = index >= 0
    )
    if (index < 0) {
      return
    }
    current.pendingDerivedOutputs = -1
    current.derivedReader = reader
    sql.insert(
      index,
      StringBuilder().also { outputs ->
        appendDerivedOutputs(
          occurrence = occurrence,
          names = body.selection.exportedNames,
          target = outputs
        )
      }
    )
  }

  /** Names are allocated before the bodies render, so a body can reference any of the declarations. */
  fun renderWith(declarations: List<CteDeclaration>) {
    declarations.forEach { declaration ->
      declaration.name?.let { name ->
        val reserved = reservedCteNames
          ?: MutableScatterSet<String>(initialCapacity = declarations.size).also { reservedCteNames = it }
        reserved.add(name.lowercase())
      }
    }
    val bodies = declarations.map(::cteBody)
    sql.append("WITH ")
    declarations.forEachIndexed { index, declaration ->
      if (index != 0) {
        sql.append(", ")
      }
      val body = bodies[index]
      sql
        .append(body.quotedName)
        .append(" AS (")
      body.reader = renderBody(
        body = declaration.node,
        read = true
      )
      sql.append(')')
    }
    sql.append(' ')
  }

  fun cteName(declaration: CteDeclaration) = cteBody(declaration).quotedName

  /** A declaration that no enclosing WITH rendered lists `*`, and SQLite reports the missing table. */
  fun appendCteOutputs(occurrence: Table<*>, declaration: CteDeclaration) {
    when (cteBody(declaration).reader) {
      null -> sql.append('*')
      else -> appendDerivedOutputs(
        occurrence = occurrence,
        names = declaration.node.selection.exportedNames,
        target = sql
      )
    }
  }

  fun cteReader(declaration: CteDeclaration) = cteBody(declaration).reader
    ?: ExecutionReader<Nothing> { error("A CTE was read without a WITH declaring it") }

  private fun cteBody(declaration: CteDeclaration): CteBody {
    val bodies = ctes ?: MutableScatterMap<CteDeclaration, CteBody>(initialCapacity = 2).also { ctes = it }
    return bodies[declaration] ?: CteBody(quoteSqlIdentifier(declaration.name ?: nextCteName()))
      .also { bodies[declaration] = it }
  }

  private fun nextCteName(): String {
    var name: String
    do {
      lastCteNumber++
      name = when {
        describing -> "{cte$lastCteNumber}"
        else -> "cte$lastCteNumber"
      }
    } while (reservedCteNames?.contains(name) == true)
    return name
  }

  private fun renderBody(body: SelectNode<*, *>, read: Boolean) = inScope(
    next = SourceScope(
      enclosing = scope.enclosing,
      kind = DERIVED,
      exportedNames = body.selection.exportedNames
    )
  ) {
    body.render(this)
    when {
      read -> body.selection.createExecutionReader(this)
      else -> null
    }
  }

  private inline fun <R> inScope(next: SourceScope, render: () -> R): R {
    val current = scope
    scope = next
    return render().also { scope = current }
  }

  fun deferDerivedOutputs() {
    scope.pendingDerivedOutputs = sql.length
  }

  private fun appendDerivedOutputs(occurrence: Table<*>, names: List<String>, target: StringBuilder) {
    val named = namesFieldOutputs
    val qualifier = occurrence.qualifier(this)
    names.forEachIndexed { index, name ->
      if (index != 0) {
        target.append(", ")
      }
      if (qualifier.isNotEmpty()) {
        target
          .append(qualifier)
          .append('.')
      }
      val quotedName = quoteSqlIdentifier(name)
      target.append(quotedName)
      if (named) {
        target
          .append(" AS ")
          .append(quotedName)
      }
    }
  }

  val derivedReader get() = scope.derivedReader

  fun addSource(table: Table<*>, qualifier: String = table.qualifier(this)) {
    val scope = scope
    scope.positions[table] = scope.qualifiers.size
    scope.qualifiers += qualifier
    scope.visibleSources = scope.qualifiers.size
  }

  fun addSourceAlias(table: Table<*>, occurrence: Table<*>) {
    val scope = scope
    scope.positions[table] = scope.positions[occurrence]
    if (scope.nullExtendedSources?.contains(occurrence) == true) {
      addNullExtended(table)
    }
  }

  fun hasEnclosingSource(table: Table<*>): Boolean {
    var enclosing = scope.enclosing
    while (enclosing != null) {
      if (enclosing.positions.containsKey(table)) {
        return true
      }
      enclosing = enclosing.enclosing
    }
    return describing
  }

  fun hasEnclosingSource(predicate: (Table<*>) -> Boolean): Boolean {
    var enclosing = scope.enclosing
    while (enclosing != null) {
      enclosing.positions.forEach { table, _ ->
        if (predicate(table)) {
          return true
        }
      }
      enclosing = enclosing.enclosing
    }
    return false
  }

  fun hasEnclosingName(name: String): Boolean {
    var enclosing = scope.enclosing ?: return false
    val qualifier = quoteSqlIdentifier(name)
    while (true) {
      val named = enclosing.qualifiers.any {
        it.equals(
          other = qualifier,
          ignoreCase = true
        )
      }
      if (named) {
        return true
      }
      enclosing = enclosing.enclosing ?: return false
    }
  }

  fun addNullExtended(table: Table<*>) {
    val nullExtended = scope.nullExtendedSources
      ?: MutableScatterSet<Table<*>>(initialCapacity = 1).also { scope.nullExtendedSources = it }
    nullExtended.add(table)
  }

  fun nullExtendPrefix() {
    scope.nullExtendedPrefix = scope.qualifiers.size
  }

  fun isNullExtended(table: Table<*>) = scope.let { scope ->
    scope.positions.getOrDefault(
      key = table,
      defaultValue = scope.nullExtendedPrefix
    ) < scope.nullExtendedPrefix || scope.nullExtendedSources?.contains(table) == true
  }

  inline fun <T> renderList(items: List<T>, render: (T) -> Unit) = renderList(count = items.size) { render(items[it]) }

  inline fun renderList(count: Int, render: (Int) -> Unit) {
    for (index in 0..<count) {
      if (index != 0) {
        sql.append(", ")
      }
      render(index)
    }
  }

  fun limitVisibleSources(count: Int) {
    scope.visibleSources = count
  }

  fun requireOccurrence(table: Table<*>) {
    qualifier(table)
  }

  fun qualifier(table: Table<*>) = visibleQualifier(table) ?: missingOccurrenceQualifier(table)

  fun entityQualifier(table: Table<*>) = visibleQualifier(table) ?: run {
    require(describing) { missingEntityOccurrence(table) }
    table.sqlQualifier
  }

  private fun visibleQualifier(table: Table<*>): String? {
    var current: SourceScope? = scope
    while (current != null) {
      val position = current.positions.getOrDefault(
        key = table,
        defaultValue = -1
      )
      if (position >= 0) {
        return when {
          position < current.visibleSources -> current.qualifiers[position]
          else -> null
        }
      }
      current = current.enclosing
    }
    return null
  }

  private fun missingOccurrenceQualifier(table: Table<*>) = when {
    describing -> table.sqlQualifier
    insertingValues && root -> throw IllegalArgumentException(INSERT_VALUES_FIELD)
    else -> throw IllegalArgumentException(MISSING_OCCURRENCE)
  }

  fun nameOutput(expression: SqlExpression<*, *, *>) {
    val alias = expression.quotedOutputAlias
    when {
      namesFieldOutputs -> {
        val name = scope.nextExportedName()
        sql.append(" AS ")
        when (alias) {
          null -> sql.appendQuotedSqlIdentifier(name)
          else -> sql.append(alias)
        }
      }
      alias != null -> sql
        .append(" AS ")
        .append(alias)
    }
  }

  val namesFieldOutputs get() = scope.kind == DERIVED || definition != null && scope.kind == ROOT

  fun nameFieldOutput() {
    sql
      .append(" AS ")
      .appendQuotedSqlIdentifier(scope.nextExportedName())
  }

  fun bind(argument: NativeValue) {
    when (definition) {
      null -> {
        sql.append('?')
        bindings += argument
      }
      else -> appendLiteral(argument)
    }
  }

  fun bind(parameter: ParameterIdentity<*>) {
    require(definition == null) {
      "Cannot create view '${definition?.viewName}': its definition cannot take parameter '${parameter.name}'"
    }
    sql.append('?')
    bindings += parameter
    val identities = parameters ?: MutableScatterSet<ParameterIdentity<*>>().also { parameters = it }
    identities.add(parameter)
  }

  fun appendLiteral(argument: NativeValue) = appendNativeLiteral(
    sql = sql,
    value = argument
  )
}

private class CteBody(val quotedName: String) {
  var reader: ExecutionReader<*>? = null
}

internal class DefinitionFacts(
  val viewName: String,
  val exportedNames: List<String>
)

private const val MISSING_OCCURRENCE =
  "A field requires the exact table occurrence of its FROM, UPDATE, DELETE or INSERT"

internal fun missingEntityOccurrence(table: Table<*>) =
  "The ${table.referenceName} entity is not a source of this query; name it in FROM or a join"

private const val INSERT_VALUES_FIELD = "INSERT VALUES cannot read a field outside a subquery"

internal fun derivedOutputName(expression: SqlExpression<*, *, *>, index: Int) = expression.outputAlias
  ?: (expression.node as? ExpressionNode.Field)?.let { field ->
    fieldOutputName(
      table = field.table,
      name = field.name
    )
  }
  ?: "_${index + 1}"

internal fun fieldOutputName(table: Table<*>, name: String): String {
  val occurrence = table.alias ?: table.name
  return when {
    occurrence.isEmpty() -> name
    else -> "$occurrence.$name"
  }
}

private enum class ScopeKind {
  ROOT,
  NESTED,
  DERIVED,
  ARM
}

private class SourceScope(
  val enclosing: SourceScope?,
  val kind: ScopeKind,
  val exportedNames: List<String> = emptyList()
) {
  val positions = MutableObjectIntMap<Table<*>>(initialCapacity = 1)
  val qualifiers = ArrayList<String>(1)
  var visibleSources = 0
  var nullExtendedPrefix = 0
  var nullExtendedSources: MutableScatterSet<Table<*>>? = null
  var relatedEntities: RelatedEntities? = null
  var valueLayout: ValueLayout? = null

  var pendingDerivedOutputs = -1
  var derivedReader: ExecutionReader<*>? = null

  private var nextExport = 0

  fun nextExportedName() = exportedNames[nextExport++]
}
