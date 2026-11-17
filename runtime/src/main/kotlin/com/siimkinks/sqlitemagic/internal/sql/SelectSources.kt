package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.FullTextIndexTable
import com.siimkinks.sqlitemagic.SqlOperator.EQUAL
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.DEEP
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.internal.TableRelationship

internal sealed class SourceOccurrence(
  val table: Table<*>,
  val pathName: String?
) {
  private var children: Array<SourceOccurrence?>? = null

  abstract val name: String

  fun child(relationship: Int) = children?.get(relationship)

  fun setChild(relationship: Int, occurrence: SourceOccurrence) {
    val current = children ?: arrayOfNulls<SourceOccurrence>(table.tableRelationships.size).also { children = it }
    current[relationship] = occurrence
  }
}

internal sealed interface JoinOccurrence {
  fun render(context: SqlRenderingContext)

  fun register(context: SqlRenderingContext)
}

private open class UserOccurrence(
  table: Table<*>,
  val index: Int
) : SourceOccurrence(
  table = table,
  pathName = null
) {
  override val name get() = table.referenceName

  fun isTargetOf(parent: SourceOccurrence, targetKey: SqliteSchemaKey) = this !== parent &&
      table.isPhysical &&
      table.objectIdentity.normalizedKey == targetKey
}

private class JoinedOccurrence(
  val join: JoinNode,
  index: Int
) : UserOccurrence(
  table = join.table,
  index = index
), JoinOccurrence {
  override fun render(context: SqlRenderingContext) = join.render(context)

  override fun register(context: SqlRenderingContext) {
    val kind = join.kind
    if (kind.nullExtendsPrefix) {
      context.nullExtendPrefix()
    }
    context.addSource(table)
    if (kind.nullExtendsJoined) {
      context.addNullExtended(table)
    }
  }

  fun joinsRelationship(parent: SourceOccurrence, target: UserOccurrence, relationship: TableRelationship): Boolean {
    val predicate = (join.constraint as? JoinConstraint.On)?.predicate
    return predicate is ExpressionNode.Binary &&
        predicate.operator == EQUAL &&
        (predicate.left.isField(parent.table, relationship.columnName) &&
            predicate.right.isField(target.table, relationship.targetIdColumnName) ||
            predicate.right.isField(parent.table, relationship.columnName) &&
            predicate.left.isField(target.table, relationship.targetIdColumnName))
  }
}

private class AutomaticOccurrence(
  table: Table<*>,
  pathName: String,
  private val parent: SourceOccurrence,
  private val relationship: TableRelationship,
  override val name: String,
  private val qualifier: String,
  private val sourceSql: String
) : SourceOccurrence(
  table = table,
  pathName = pathName
), JoinOccurrence {
  var precedesJoin = NO_JOIN

  override fun render(context: SqlRenderingContext) {
    context.sql
      .append(" LEFT JOIN ")
      .append(sourceSql)
      .append(" ON ")
      .append(context.qualifier(parent.table))
      .append('.')
      .append(relationship.quotedColumnName)
      .append(" = ")
      .append(qualifier)
      .append('.')
      .append(relationship.quotedTargetIdColumnName)
    table.addDependencies(context.dependencies)
  }

  override fun register(context: SqlRenderingContext) {
    context.addSource(
      table = table,
      qualifier = qualifier,
      name = name
    )
    if (relationship.nullable || context.isNullExtended(parent.table)) {
      context.addNullExtended(table)
    }
  }
}

private class FullTextOccurrence(
  val table: FullTextIndexTable,
  val content: UserOccurrence
) : JoinOccurrence {
  override fun render(context: SqlRenderingContext) {
    context.sql
      .append(" INNER JOIN ")
      .append(table.sqlSource)
      .append(" ON ")
      .append(table.sqlQualifier)
      .append("""."rowid" = """)
      .append(context.qualifier(content.table))
      .append('.')
      .append(table.quotedContentRowIdColumnName)
    table.addDependencies(context.dependencies)
  }

  override fun register(context: SqlRenderingContext) = context.addSource(table)
}

private const val NO_JOIN = Int.MAX_VALUE

internal class SelectSources(
  private val root: Table<*>,
  private val joins: List<JoinOccurrence>
) {
  fun render(context: SqlRenderingContext) {
    context.sql.append(" FROM ")
    root.renderSource(context)
    joins.forEachIndexed { index, join ->
      context.limitVisibleSources(index + 2)
      join.render(context)
    }
    context.limitVisibleSources(joins.size + 1)
    context.markFromEnd()
  }
}

internal fun prepareSources(node: SelectNode<*, *>, context: SqlRenderingContext): SelectSources? {
  val root = node.source ?: return null
  val planner = RelationshipPlanner(
    root = root,
    joins = node.joins,
    context = context
  )
  planner.joinUnjoinedFields(node)
  if (!context.nested) {
    context.relatedEntities = node.selection.planRelatedEntities(planner)
    context.valueLayout = node.selection.planValues(planner)
  }
  val joins = planner.orderedJoins()
  planner.register(joins)
  if (context.root) {
    context.definition?.occurrences?.let(planner::exportOccurrences)
  }
  return SelectSources(
    root = root,
    joins = joins
  )
}

internal class RelationshipPlanner(
  root: Table<*>,
  joins: List<JoinNode>,
  private val context: SqlRenderingContext
) {
  private val rootOccurrence = UserOccurrence(
    table = root,
    index = 0
  )
  private val joined = List(joins.size) { index ->
    JoinedOccurrence(
      join = joins[index],
      index = index + 1
    )
  }
  private val users = buildList(joins.size + 1) {
    add(rootOccurrence)
    addAll(joined)
  }
  private val automatic = ArrayList<AutomaticOccurrence>()
  private var fullText: MutableList<FullTextOccurrence>? = null

  private var automaticTables: MutableScatterSet<Table<*>>? = null
  private var usedNames: MutableScatterSet<String>? = null
  private var readers: MutableObjectIntMap<Table<*>>? = null
  private var targets: MutableList<Table<*>>? = null
  private var resolvedFields: MutableList<Pair<Table<*>, SourceOccurrence>>? = null
  private var readingJoin = NO_JOIN
  private val fieldCollector: (ExpressionNode.Field) -> Unit = ::collect
  private val tableCollector: (Table<*>) -> Unit = ::collect

  fun joinUnjoinedFields(node: SelectNode<*, *>) {
    node.selection.forEachField(fieldCollector)
    node.selection.forEachEntityValue(tableCollector)
    node.joins.forEachIndexed { index, join ->
      readingJoin = index
      (join.constraint as? JoinConstraint.On)?.predicate?.forEachField(fieldCollector)
    }
    readingJoin = NO_JOIN
    node.where?.forEachField(fieldCollector)
    node.groupings.forEach { it.forEachField(fieldCollector) }
    node.having?.forEachField(fieldCollector)
    node.orderings.forEach { it.node.forEachField(fieldCollector) }
    val targets = targets ?: return
    val readers = checkNotNull(readers)
    val resolved = ArrayList<Pair<Table<*>, SourceOccurrence>>(targets.size).also { resolvedFields = it }
    targets.forEach { target ->
      val route = singleRoute(target) ?: return@forEach
      val earliestReadingJoin = readers[target]
      val precedesJoin = when {
        route.start.index <= earliestReadingJoin -> earliestReadingJoin
        else -> NO_JOIN
      }
      var occurrence: SourceOccurrence = route.start
      route.relationships.forEach { relationship ->
        val child = existingChild(
          parent = occurrence,
          relationship = relationship
        ) ?: automaticChild(
          parent = occurrence,
          relationship = relationship
        )
        if (child is AutomaticOccurrence) {
          child.precedesJoin = minOf(
            a = child.precedesJoin,
            b = precedesJoin
          )
        }
        occurrence = child
      }
      resolved += target to occurrence
    }
  }

  fun entityRead(deep: Boolean) = when {
    rootOccurrence.table.tableRelationships.isEmpty() -> null
    else -> entityRead(
      occurrence = rootOccurrence,
      startIndex = 0,
      deep = deep
    )
  }

  fun entityValueRead(table: Table<*>, startIndex: Int, deep: Boolean): RelatedEntities {
    val occurrence = requireNotNull(occurrenceOf(table)) { missingEntityOccurrence(table) }
    return when (val view = table.viewDefinition) {
      null -> entityRead(
        occurrence = occurrence,
        startIndex = startIndex,
        deep = deep
      )
      else -> RelatedEntities(
        table = table,
        children = when (val blocks = view.blocks) {
          null -> emptyArray()
          else -> Array(blocks.size) { blocks[it]?.shifted(startIndex) }
        },
        startIndex = startIndex,
        width = view.columnNames.size
      )
    }
  }

  private fun entityRead(occurrence: SourceOccurrence, startIndex: Int, deep: Boolean): RelatedEntities {
    val table = occurrence.table
    val rootChildren = arrayOfNulls<RelatedEntities>(table.tableRelationships.size)
    val joinedReads = ArrayList<RelatedEntities>()
    var nextIndex = startIndex + table.selectedEntityFields().size
    val queue = ArrayDeque<Pair<SourceOccurrence, Array<RelatedEntities?>>>()
    queue.add(occurrence to rootChildren)
    while (queue.isNotEmpty()) {
      val (occurrence, children) = queue.removeFirst()
      occurrence.table.tableRelationships.forEachIndexed { index, relationship ->
        val isJoined = when (relationship.read) {
          ALWAYS -> true
          DEEP -> deep
          ID_ONLY -> false
        }
        if (isJoined) {
          val child = relatedChild(
            parent = occurrence,
            relationship = index
          ) ?: automaticChild(
            parent = occurrence,
            relationship = index
          )
          val grandchildren = arrayOfNulls<RelatedEntities>(child.table.tableRelationships.size)
          val fields = child.table.selectedEntityFields()
          val read = RelatedEntities(
            table = child.table,
            children = grandchildren,
            startIndex = nextIndex,
            width = fields.size,
            idOffset = fields.indexOfFirst { it.name == relationship.targetIdColumnName }
          )
          nextIndex += fields.size
          children[index] = read
          joinedReads += read
          queue.add(child to grandchildren)
        }
      }
    }
    return RelatedEntities(
      table = table,
      children = rootChildren,
      startIndex = startIndex,
      width = table.selectedEntityFields().size,
      joined = joinedReads
    )
  }

  fun occurrenceOf(table: Table<*>) =
    users.firstOrNull { it.table === table }
      ?: resolvedFields?.firstOrNull { (handle) -> handle === table }?.second
      ?: automatic.firstOrNull { it.table === table }

  fun orderedJoins(): List<JoinOccurrence> {
    val fullText = fullText
    if (automatic.isEmpty() && fullText == null) {
      return joined
    }
    val ordered = ArrayList<JoinOccurrence>(joined.size + automatic.size + (fullText?.size ?: 0))
    fullText?.filterTo(ordered) { it.content === rootOccurrence }
    joined.forEachIndexed { joinIndex, join ->
      automatic.filterTo(ordered) { it.precedesJoin == joinIndex }
      ordered += join
      fullText?.filterTo(ordered) { it.content === join }
    }
    automatic.filterTo(ordered) { it.precedesJoin == NO_JOIN }
    return ordered
  }

  fun register(joins: List<JoinOccurrence>) {
    context.addSource(rootOccurrence.table)
    joins.forEach { it.register(context) }
    resolvedFields?.forEach { (table, occurrence) ->
      if (occurrence.table !== table) {
        context.addSourceAlias(
          table = table,
          occurrence = occurrence.table
        )
      }
    }
  }

  fun exportOccurrences(destination: MutableScatterMap<String, Array<String?>>) {
    users.forEach { user ->
      export(
        occurrence = user,
        destination = destination
      )
    }
    automatic.forEach { occurrence ->
      export(
        occurrence = occurrence,
        destination = destination
      )
    }
  }

  private fun export(occurrence: SourceOccurrence, destination: MutableScatterMap<String, Array<String?>>) {
    val relationships = occurrence.table.tableRelationships
    if (relationships.isEmpty()) {
      return
    }
    destination[occurrence.name.lowercase()] = Array(relationships.size) { index ->
      relatedChild(
        parent = occurrence,
        relationship = index
      )?.name
    }
  }

  private fun collect(field: ExpressionNode.Field) = collect(field.table)

  private fun collect(table: Table<*>) {
    when {
      !table.isDefaultHandle || isOccurrenceTable(table) -> Unit
      table is FullTextIndexTable -> collectFullText(table)
      !context.hasEnclosingSource(table) -> collectReader(table)
    }
  }

  private fun collectFullText(index: FullTextIndexTable) {
    when (val content = localFullTextContent(index)) {
      null -> require(context.hasEnclosingSource(index)) { missingFullTextContent(index) }
      else -> (fullText ?: ArrayList<FullTextOccurrence>(1).also { fullText = it }) += FullTextOccurrence(
        table = index,
        content = content
      )
    }
  }

  private fun collectReader(table: Table<*>) {
    val readers = readers ?: MutableObjectIntMap<Table<*>>().also { readers = it }
    when {
      readers.containsKey(table) -> readers[table] = minOf(
        a = readers[table],
        b = readingJoin
      )
      else -> {
        readers[table] = readingJoin
        (targets ?: ArrayList<Table<*>>().also { targets = it }) += table
      }
    }
  }

  private fun isOccurrenceTable(table: Table<*>) = users.any { it.table === table } ||
      automaticTables?.contains(table) == true ||
      fullText?.any { it.table === table } == true

  private fun localFullTextContent(index: FullTextIndexTable): UserOccurrence? {
    val content = index.contentTable
    val contentKey = content.objectIdentity.normalizedKey
    val occurrence = users.firstOrNull { it.isPhysicalOccurrenceOf(contentKey) } ?: return null
    require(users.lastOrNull { it.isPhysicalOccurrenceOf(contentKey) } === occurrence) {
      val occurrences = users.filter { it.isPhysicalOccurrenceOf(contentKey) }
      "Full-text index ${index.name} searches ${content.name}, which is a source of the query " +
          "${occurrences.size} times (${occurrences.joinToString { it.name }}); join ${index.name} explicitly"
    }
    return occurrence
  }

  private fun UserOccurrence.isPhysicalOccurrenceOf(key: SqliteSchemaKey) = table.isPhysicalTableOf(key)

  private fun Table<*>.isPhysicalTableOf(key: SqliteSchemaKey) = isPhysical && objectIdentity.normalizedKey == key

  private fun missingFullTextContent(index: FullTextIndexTable): String {
    val content = index.contentTable
    val contentKey = content.objectIdentity.normalizedKey
    return when {
      context.hasEnclosingSource { it.isPhysicalTableOf(contentKey) } -> "Full-text index ${index.name} searches " +
          "${content.name}, which an enclosing query reads without searching ${index.name}; search ${index.name} " +
          "in that query"
      reachesThroughRelationships(contentKey) -> "Full-text index ${index.name} searches " +
          "${content.name}, which the query joins only through a relationship; join ${content.name} explicitly " +
          "with INNER_JOIN(…)"
      else -> "Full-text index ${index.name} searches ${content.name}, which is not a source of the query"
    }
  }

  private fun reachesThroughRelationships(key: SqliteSchemaKey): Boolean {
    val visited = MutableScatterSet<SqliteSchemaKey>()
    val pending = ArrayDeque(users.map(UserOccurrence::table))
    while (pending.isNotEmpty()) {
      pending.removeFirst().tableRelationships.forEach { relationship ->
        val target = relationship.target()
        val targetKey = target.objectIdentity.normalizedKey
        if (targetKey == key) {
          return true
        }
        if (visited.add(targetKey)) {
          pending += target
        }
      }
    }
    return false
  }

  fun relatedChild(parent: SourceOccurrence, relationship: Int) = existingChild(
    parent = parent,
    relationship = relationship
  ) ?: exactUserJoin(
    parent = parent,
    relationship = parent.table.tableRelationships[relationship],
    everyRow = false
  )

  private fun existingChild(parent: SourceOccurrence, relationship: Int) = parent.child(relationship)
    ?: exactUserJoin(
      parent = parent,
      relationship = parent.table.tableRelationships[relationship],
      everyRow = true
    )?.also { join ->
      parent.setChild(
        relationship = relationship,
        occurrence = join
      )
    }

  private fun automaticChild(parent: SourceOccurrence, relationship: Int) = automaticOccurrence(
    parent = parent,
    relationship = parent.table.tableRelationships[relationship]
  ).also { child ->
    parent.setChild(
      relationship = relationship,
      occurrence = child
    )
  }

  private fun automaticOccurrence(parent: SourceOccurrence, relationship: TableRelationship): AutomaticOccurrence {
    val target = relationship.target()
    val pathName = parent.pathName?.let { "${it}_${relationship.columnName}" }
      ?: relationship.columnName
    val firstOccurrence = !isOccurrenceTable(target)
    val usedNames = usedNames ?: MutableScatterSet<String>(initialCapacity = users.size + 1).also { names ->
      users.forEach { names += it.table.referenceName.lowercase() }
      usedNames = names
    }
    val name = uniqueName(
      base = when {
        firstOccurrence && !usedNames.isTaken(target.name) -> target.name
        else -> pathName
      },
      usedNames = usedNames
    )
    usedNames += name.lowercase()
    val table = when {
      firstOccurrence -> target
      else -> target.AS(name)
    }
    (automaticTables ?: MutableScatterSet<Table<*>>().also { automaticTables = it }) += table
    val qualifier = quoteSqlIdentifier(name)
    return AutomaticOccurrence(
      table = table,
      pathName = pathName,
      parent = parent,
      relationship = relationship,
      name = name,
      qualifier = qualifier,
      sourceSql = when {
        table === target && name != target.name -> "${target.sqlSource} AS $qualifier"
        else -> table.sqlSource
      }
    ).also(automatic::add)
  }

  private fun uniqueName(base: String, usedNames: MutableScatterSet<String>): String {
    if (!usedNames.isTaken(base)) {
      return base
    }
    var suffix = 2
    while (usedNames.isTaken("${base}_$suffix")) {
      suffix++
    }
    return "${base}_$suffix"
  }

  private fun MutableScatterSet<String>.isTaken(name: String) =
    name.lowercase() in this || context.hasEnclosingName(name)

  private fun exactUserJoin(
    parent: SourceOccurrence,
    relationship: TableRelationship,
    everyRow: Boolean
  ): UserOccurrence? {
    val targetKey = relationship.target().objectIdentity.normalizedKey
    return joined.firstOrNull { target ->
      !(everyRow && target.join.kind.nullExtendsPrefix) && target.isTargetOf(
        parent = parent,
        targetKey = targetKey
      ) && target.joinsRelationship(
        parent = parent,
        target = target,
        relationship = relationship
      )
    } ?: (parent as? JoinedOccurrence)
      ?.takeUnless { it.join.kind.nullExtendsPrefix || everyRow && it.join.kind.nullExtendsJoined }
      ?.let { constrained ->
        users.firstOrNull { target ->
          target.index < constrained.index && target.isTargetOf(
            parent = parent,
            targetKey = targetKey
          ) && constrained.joinsRelationship(
            parent = parent,
            target = target,
            relationship = relationship
          )
        }
      }
  }

  private class Route(
    val start: UserOccurrence,
    val relationships: IntArray
  )

  private fun singleRoute(target: Table<*>): Route? {
    val routes = ArrayList<Route>(1)
    val path = ArrayList<Int>()
    val visited = ArrayList<SqliteSchemaKey>()
    users.forEach { start ->
      fun search(node: SourceOccurrence?, table: Table<*>) {
        table.tableRelationships.forEachIndexed { index, relationship ->
          val next = relationship.target()
          val existing = node?.let { parent ->
            existingChild(
              parent = parent,
              relationship = index
            )
          }
          when {
            next === target -> routes += Route(
              start = start,
              relationships = (path + index).toIntArray()
            )
            existing is UserOccurrence -> Unit
            next.objectIdentity.normalizedKey !in visited -> {
              path += index
              visited += next.objectIdentity.normalizedKey
              search(
                node = existing,
                table = next
              )
              visited.removeAt(visited.lastIndex)
              path.removeAt(path.lastIndex)
            }
          }
        }
      }

      visited += start.table.objectIdentity.normalizedKey
      search(
        node = start,
        table = start.table
      )
      visited.clear()
    }
    require(routes.size <= 1) {
      "Several relationships lead to ${target.name}: ${routes.joinToString(transform = ::describe)}; " +
          "join the one you mean"
    }
    return routes.singleOrNull()
  }

  private fun describe(route: Route): String {
    var table: Table<*> = route.start.table
    return buildString {
      append(table.referenceName)
      route.relationships.forEach { index ->
        val relationship = table.tableRelationships[index]
        append('.')
        append(relationship.columnName)
        table = relationship.target()
      }
    }
  }
}

private fun ExpressionNode.isField(table: Table<*>, name: String) =
  this is ExpressionNode.Field && this.table === table && this.name == name
