package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.DEEP
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.QualifiedColumns
import com.siimkinks.sqlitemagic.internal.sql.fieldOutputName

internal class EntityExport(
  val table: Table<*>,
  val deep: Boolean,
  val names: List<String>,
  read: RelatedEntities? = null
) {
  private var tree = read

  private var renderedColumns: QualifiedColumns? = null

  fun read(startIndex: Int): RelatedEntities {
    val cached = tree
    if (cached != null && cached.startIndex == startIndex) {
      return cached
    }
    return table
      .entityValueTree(
        startIndex = startIndex,
        deep = deep
      )
      .also { tree = it }
  }

  fun renderedColumns(qualifier: String): String {
    val cached = renderedColumns
    if (cached != null && cached.qualifier == qualifier) {
      return cached.sql
    }
    val rendered = QualifiedColumns(
      qualifier = qualifier,
      quotedNames = names.map(::quoteSqlIdentifier)
    )
    renderedColumns = rendered
    return rendered.sql
  }
}

internal class ExportedEntity(
  val value: SelectValue<*, *>,
  val export: EntityExport
)

internal fun EntityValue<*>.export(deep: Boolean) = when (this) {
  is Table<*> -> {
    val read = entityValueTree(
      startIndex = 0,
      deep = deep
    )
    EntityExport(
      table = this,
      deep = deep,
      names = entityExportNames(
        table = this,
        read = read
      ),
      read = read
    )
  }
  is DerivedEntity<*> -> EntityExport(
    table = export.table,
    deep = export.deep,
    names = export.names.map { name ->
      fieldOutputName(
        table = occurrence,
        name = name
      )
    }
  )
}

internal val EntityValue<*>.readTable
  get() = when (this) {
    is Table<*> -> this
    is DerivedEntity<*> -> export.table
  }

internal val EntityValue<*>.reference
  get() = when (this) {
    is Table<*> -> referenceName
    is DerivedEntity<*> -> "$occurrence[${export.table.referenceName}]"
  }

internal val EntityValue<*>.label
  get() = when (this) {
    is Table<*> -> "$referenceName entity"
    is DerivedEntity<*> -> "${export.table.referenceName} entity of $occurrence"
  }

internal fun Table<*>.entityValueTree(startIndex: Int, deep: Boolean): RelatedEntities =
  when (val view = viewDefinition) {
    null -> entityReadTree(
      root = this,
      startIndex = startIndex,
      deep = deep,
      tableOf = { it },
      childOf = { parent, relationship -> parent.tableRelationships[relationship].target() }
    )
    else -> RelatedEntities(
      table = this,
      children = when (val blocks = view.blocks) {
        null -> emptyArray()
        else -> Array(blocks.size) { blocks[it]?.shifted(startIndex) }
      },
      startIndex = startIndex,
      width = view.columnNames.size
    )
  }

internal inline fun <N> entityReadTree(
  root: N,
  startIndex: Int,
  deep: Boolean,
  tableOf: (N) -> Table<*>,
  childOf: (parent: N, relationship: Int) -> N
): RelatedEntities {
  val table = tableOf(root)
  val rootChildren = arrayOfNulls<RelatedEntities>(table.tableRelationships.size)
  val joinedReads = ArrayList<RelatedEntities>()
  var nextIndex = startIndex + table.selectedEntityFields().size
  val queue = ArrayDeque<Pair<N, Array<RelatedEntities?>>>()
  queue.add(root to rootChildren)
  while (queue.isNotEmpty()) {
    val (parent, children) = queue.removeFirst()
    tableOf(parent).tableRelationships.forEachIndexed { index, relationship ->
      val isJoined = when (relationship.read) {
        ALWAYS -> true
        DEEP -> deep
        ID_ONLY -> false
      }
      if (isJoined) {
        val child = childOf(parent, index)
        val childTable = tableOf(child)
        val grandchildren = arrayOfNulls<RelatedEntities>(childTable.tableRelationships.size)
        val fields = childTable.selectedEntityFields()
        val read = RelatedEntities(
          table = childTable,
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

internal fun entityExportNames(table: Table<*>, read: RelatedEntities?) = buildList {
  when (val view = table.viewDefinition) {
    null -> table.selectedEntityFields().forEach { field ->
      add(
        fieldOutputName(
          table = table,
          name = field.name
        )
      )
    }
    else -> view.columnNames.forEach { name ->
      add(
        fieldOutputName(
          table = table,
          name = name
        )
      )
    }
  }
  read
    ?.joinedPrefixes(table.referenceName)
    ?.forEachIndexed { index, prefix ->
      read.joined[index].table.selectedEntityFields().forEach { field -> add("$prefix.${field.name}") }
    }
}

internal fun RelatedEntities.joinedPrefixes(prefix: String): List<String> {
  if (joined.isEmpty()) {
    return emptyList()
  }
  val prefixes = ArrayList<String>(joined.size)
  val queue = ArrayDeque<Pair<RelatedEntities, String>>()
  queue.add(this to prefix)
  while (queue.isNotEmpty()) {
    val (parent, parentPrefix) = queue.removeFirst()
    val relationships = parent.table.tableRelationships
    parent.children.forEachIndexed { index, child ->
      if (child != null) {
        val childPrefix = "$parentPrefix.${relationships[index].columnName}"
        prefixes += childPrefix
        queue.add(child to childPrefix)
      }
    }
  }
  return prefixes
}
