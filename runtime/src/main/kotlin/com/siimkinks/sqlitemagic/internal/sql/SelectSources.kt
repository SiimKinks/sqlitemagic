package com.siimkinks.sqlitemagic.internal.sql

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

internal open class UserOccurrence(
  table: Table<*>,
  val index: Int
) : SourceOccurrence(
  table = table,
  pathName = null
) {
  override val name get() = table.referenceName
}

internal class JoinedOccurrence(
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
}

internal class AutomaticOccurrence(
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
  }
}

internal fun prepareSources(node: SelectNode<*, *>, context: SqlRenderingContext): SelectSources? {
  val root = node.source ?: return null
  val planner = RelationshipPlanner(
    root = root,
    joins = node.joins,
    isEnclosingSource = context::hasEnclosingSource
  )
  planner.joinUnjoinedFields(node)
  context.relatedEntities = when {
    context.nested -> null
    else -> node.selection.planRelatedEntities(planner)
  }
  val joins = planner.orderedJoins()
  planner.register(
    context = context,
    joins = joins
  )
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
  private val isEnclosingSource: (Table<*>) -> Boolean
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

  private var automaticTables: MutableScatterSet<Table<*>>? = null
  private var usedNames: MutableScatterSet<String>? = null
  private var readers: MutableObjectIntMap<Table<*>>? = null
  private var targets: MutableList<Table<*>>? = null
  private var resolvedFields: MutableList<Pair<Table<*>, SourceOccurrence>>? = null
  private var readingJoin = NO_JOIN
  private val fieldCollector: (ExpressionNode.Field) -> Unit = ::collect

  fun joinUnjoinedFields(node: SelectNode<*, *>) {
    node.selection.forEachField(fieldCollector)
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
        val child = childOf(
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

  fun entityRead(deep: Boolean): RelatedEntities? {
    val relationships = rootOccurrence.table.tableRelationships
    if (relationships.isEmpty()) {
      return null
    }
    val rootChildren = arrayOfNulls<RelatedEntities>(relationships.size)
    val joinedReads = ArrayList<RelatedEntities>()
    var nextIndex = rootOccurrence.table.selectedEntityFields().size
    val queue = ArrayDeque<Pair<SourceOccurrence, Array<RelatedEntities?>>>()
    queue.add(rootOccurrence to rootChildren)
    while (queue.isNotEmpty()) {
      val (occurrence, children) = queue.removeFirst()
      occurrence.table.tableRelationships.forEachIndexed { index, relationship ->
        val isJoined = when (relationship.read) {
          ALWAYS -> true
          DEEP -> deep
          ID_ONLY -> false
        }
        if (isJoined) {
          val child = childOf(
            parent = occurrence,
            relationship = index
          )
          val grandchildren = arrayOfNulls<RelatedEntities>(child.table.tableRelationships.size)
          val read = RelatedEntities(
            table = child.table,
            children = grandchildren,
            startIndex = nextIndex
          )
          nextIndex += child.table.selectedEntityFields().size
          children[index] = read
          joinedReads += read
          queue.add(child to grandchildren)
        }
      }
    }
    return RelatedEntities(
      table = rootOccurrence.table,
      children = rootChildren,
      joined = joinedReads
    )
  }

  fun occurrenceOf(table: Table<*>) =
    users.firstOrNull { it.table === table }
      ?: resolvedFields?.firstOrNull { (handle) -> handle === table }?.second
      ?: automatic.firstOrNull { it.table === table }

  fun orderedJoins(): List<JoinOccurrence> {
    if (automatic.isEmpty()) {
      return joined
    }
    val ordered = ArrayList<JoinOccurrence>(joined.size + automatic.size)
    val placed = MutableScatterSet<AutomaticOccurrence>(initialCapacity = automatic.size)
    joined.forEachIndexed { joinIndex, join ->
      automatic.forEach { occurrence ->
        if (occurrence.precedesJoin == joinIndex && placed.add(occurrence)) {
          ordered += occurrence
        }
      }
      ordered += join
    }
    automatic.filterTo(ordered) { it !in placed }
    return ordered
  }

  fun register(context: SqlRenderingContext, joins: List<JoinOccurrence>) {
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
      existingChild(
        parent = occurrence,
        relationship = index
      )?.name
    }
  }

  private fun collect(field: ExpressionNode.Field) {
    val table = field.table
    if (!table.isDefaultHandle || isOccurrenceTable(table) || isEnclosingSource(table)) {
      return
    }
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

  private fun isOccurrenceTable(table: Table<*>) =
    users.any { it.table === table } || automaticTables?.contains(table) == true

  fun existingChild(parent: SourceOccurrence, relationship: Int) = parent.child(relationship)
    ?: exactUserJoin(
      parent = parent,
      relationship = parent.table.tableRelationships[relationship]
    )?.also { join ->
      parent.setChild(
        relationship = relationship,
        occurrence = join
      )
    }

  private fun childOf(parent: SourceOccurrence, relationship: Int) = existingChild(
    parent = parent,
    relationship = relationship
  ) ?: automaticOccurrence(
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
        firstOccurrence && target.name.lowercase() !in usedNames -> target.name
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
    if (base.lowercase() !in usedNames) {
      return base
    }
    var suffix = 2
    while ("${base}_$suffix".lowercase() in usedNames) {
      suffix++
    }
    return "${base}_$suffix"
  }

  private fun exactUserJoin(parent: SourceOccurrence, relationship: TableRelationship): JoinedOccurrence? {
    val targetKey = relationship.target().objectIdentity.normalizedKey
    return joined.firstOrNull { user ->
      val predicate = (user.join.constraint as? JoinConstraint.On)?.predicate
      user.table.isPhysical &&
          user.table.objectIdentity.normalizedKey == targetKey &&
          predicate is ExpressionNode.Binary &&
          predicate.operator == EQUAL &&
          (predicate.left.isField(parent.table, relationship.columnName) &&
              predicate.right.isField(user.table, relationship.targetIdColumnName) ||
              predicate.right.isField(parent.table, relationship.columnName) &&
              predicate.left.isField(user.table, relationship.targetIdColumnName))
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
    return when (routes.size) {
      0 -> null
      1 -> routes.single()
      else -> throw IllegalArgumentException(
        "Several relationships lead to ${target.name}: ${routes.joinToString(transform = ::describe)}; " +
            "join the one you mean"
      )
    }
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
