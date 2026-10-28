package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.CteDeclaration
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.KeyedField
import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
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
  val definition: DefinitionFacts? = null
) {
  val sql = StringBuilder(256)
  val bindings = mutableListOf<QueryBinding>()
  val parameters = MutableScatterSet<ParameterIdentity<*>>()
  val dependencies = QueryDependencies.Builder(collectDirectSources = definition != null)
  private var scope = SourceScope(
    enclosing = null,
    kind = ROOT
  )

  private var ctes: MutableScatterMap<CteDeclaration, CteBody>? = null
  private var reservedCteNames: MutableScatterSet<String>? = null
  private var lastCteNumber = 0

  val nested get() = scope.kind == NESTED

  val root get() = scope.kind == ROOT

  var relatedEntities: RelatedEntities?
    get() = scope.relatedEntities
    set(value) {
      scope.relatedEntities = value
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
    val rendered = renderBody(
      body = body,
      read = index >= 0
    )
    if (index < 0) {
      return
    }
    current.pendingDerivedOutputs = -1
    current.derivedReader = rendered.reader
    sql.insert(
      index,
      StringBuilder().also { outputs ->
        appendDerivedOutputs(
          occurrence = occurrence,
          names = rendered.outputNames,
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
      body.rendered = renderBody(
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
    when (val rendered = cteBody(declaration).rendered) {
      null -> sql.append('*')
      else -> appendDerivedOutputs(
        occurrence = occurrence,
        names = rendered.outputNames,
        target = sql
      )
    }
  }

  fun cteReader(declaration: CteDeclaration) = cteBody(declaration).rendered?.reader
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
      name = "cte$lastCteNumber"
    } while (reservedCteNames?.contains(name) == true)
    return name
  }

  private fun renderBody(body: SelectNode<*, *>, read: Boolean): RenderedBody {
    val derived = SourceScope(
      enclosing = scope.enclosing,
      kind = DERIVED
    )
    return inScope(derived) {
      body.render(this)
      RenderedBody(
        outputNames = derived.outputNames ?: emptyList(),
        reader = when {
          read -> body.selection.createExecutionReader(this)
          else -> null
        }
      )
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
    val scope = scope
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
      when (scope.kind) {
        DERIVED -> {
          scope.addOutputName(name)
          target
            .append(" AS ")
            .append(quotedName)
        }
        else -> nameDefinitionOutput(
          table = occurrence,
          name = name,
          target = target
        )
      }
    }
  }

  val derivedReader get() = scope.derivedReader

  fun addSource(
    table: Table<*>,
    qualifier: String = table.qualifier(this),
    name: String = table.alias ?: table.name
  ) {
    val scope = scope
    scope.positions[table] = scope.qualifiers.size
    scope.qualifiers += qualifier
    if (scope.kind == ROOT) {
      definition?.sourceNames?.add(name)
    }
    scope.visibleSources = scope.qualifiers.size
  }

  fun addSourceAlias(table: Table<*>, occurrence: Table<*>) {
    scope.positions[table] = scope.positions[occurrence]
  }

  fun hasEnclosingSource(table: Table<*>): Boolean {
    var enclosing = scope.enclosing
    while (enclosing != null) {
      if (enclosing.positions.containsKey(table)) {
        return true
      }
      enclosing = enclosing.enclosing
    }
    return false
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
    for (index in 0 until count) {
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

  fun qualifier(table: Table<*>): String {
    var current: SourceScope? = scope
    while (current != null) {
      val position = current.positions.getOrDefault(
        key = table,
        defaultValue = -1
      )
      if (position >= 0) {
        require(position < current.visibleSources) { MISSING_OCCURRENCE }
        return current.qualifiers[position]
      }
      current = current.enclosing
    }
    throw IllegalArgumentException(MISSING_OCCURRENCE)
  }

  fun nameOutput(expression: SqlExpression<*, *, *>) {
    val scope = scope
    when {
      scope.kind == DERIVED -> {
        val name = derivedOutputName(
          expression = expression,
          index = scope.outputNames?.size ?: 0
        )
        scope.addOutputName(name)
        sql
          .append(" AS ")
          .append(expression.quotedOutputAlias ?: quoteSqlIdentifier(name))
      }
      expression is KeyedField<*, *, *> -> nameKeyedOutput(expression)
      expression.quotedOutputAlias != null -> sql
        .append(" AS ")
        .append(expression.quotedOutputAlias)
      else -> (expression.node as? ExpressionNode.Field)?.let { field ->
        nameDefinitionOutput(
          table = field.table,
          name = field.name
        )
      }
    }
  }

  private fun nameKeyedOutput(field: KeyedField<*, *, *>) {
    sql
      .append(" AS ")
      .append(field.quotedOutputAlias)
    val definition = definition ?: return
    if (scope.kind != ROOT) {
      return
    }
    definition.outputs += checkNotNull(field.outputAlias).lowercase()
    val table = (field.node as ExpressionNode.Field).table
    val sourceName = definition.sourceNames[scope.positions[table]]
    definition.occurrences[sourceName.lowercase()]?.let { definition.occurrences[field.key.lowercase()] = it }
  }

  fun nameFieldOutput(table: Table<*>, name: String) {
    val scope = scope
    when (scope.kind) {
      DERIVED -> {
        val outputName = fieldOutputName(
          table = table,
          name = name
        )
        scope.addOutputName(outputName)
        sql
          .append(" AS ")
          .append(quoteSqlIdentifier(outputName))
      }
      else -> nameDefinitionOutput(
        table = table,
        name = name
      )
    }
  }

  private fun nameDefinitionOutput(table: Table<*>, name: String, target: StringBuilder = sql) {
    val definition = definition ?: return
    if (scope.kind != ROOT) {
      return
    }
    val position = scope.positions.getOrDefault(
      key = table,
      defaultValue = scope.visibleSources
    )
    require(position < scope.visibleSources) { MISSING_OCCURRENCE }
    val sourceName = definition.sourceNames[position]
    val outputName = when {
      sourceName.isEmpty() -> name
      else -> "$sourceName.$name"
    }
    definition.outputs += outputName.lowercase()
    target
      .append(" AS ")
      .append(quoteSqlIdentifier(outputName))
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
    parameters.add(parameter)
  }

  fun appendLiteral(argument: NativeValue) = appendNativeLiteral(
    sql = sql,
    value = argument
  )
}

private class RenderedBody(
  val outputNames: List<String>,
  val reader: ExecutionReader<*>?
)

private class CteBody(val quotedName: String) {
  var rendered: RenderedBody? = null
}

internal class DefinitionFacts(val viewName: String) {
  val sourceNames = ArrayList<String>(1)

  val occurrences = MutableScatterMap<String, Array<String?>>()

  val outputs = MutableScatterSet<String>()
}

private const val MISSING_OCCURRENCE = "A field requires the exact table occurrence of its FROM, UPDATE or DELETE"

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
  val kind: ScopeKind
) {
  val positions = MutableObjectIntMap<Table<*>>(initialCapacity = 1)
  val qualifiers = ArrayList<String>(1)
  var visibleSources = 0
  var nullExtendedPrefix = 0
  var nullExtendedSources: MutableScatterSet<Table<*>>? = null
  var relatedEntities: RelatedEntities? = null

  var pendingDerivedOutputs = -1
  var derivedReader: ExecutionReader<*>? = null

  var outputNames: MutableList<String>? = null

  fun addOutputName(name: String) {
    (outputNames ?: ArrayList<String>(4).also { outputNames = it }) += name
  }
}
