package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.internal.ViewBlock
import com.siimkinks.sqlitemagic.internal.ViewDefinition

internal fun compileViewDefinition(
  query: SelectQuery<*, *>,
  viewName: String,
  columns: List<String>,
  blocks: List<ViewBlock>
): ViewDefinition {
  val node = query.node
  require(node.connection == null) {
    "Cannot create view '$viewName': a view definition cannot use a connection"
  }
  val facts = DefinitionFacts(viewName)
  val context = SqlRenderingContext(facts)
  node.render(context)
  val columnNames = ArrayList<String>(columns.size + blocks.size).apply { addAll(columns) }
  val blockReads = when {
    blocks.isEmpty() -> null
    else -> ViewBlockLayout(
      facts = facts,
      columnNames = columnNames
    ).let { layout ->
      Array<RelatedEntities?>(blocks.size) { layout.add(blocks[it]) }
    }
  }
  return ViewDefinition(
    sql = context.sql.toString(),
    queryDependencies = context.dependencies.build(),
    columnNames = columnNames.toTypedArray(),
    blocks = blockReads
  )
}

private class ViewBlockLayout(
  private val facts: DefinitionFacts,
  private val columnNames: MutableList<String>
) {
  fun add(block: ViewBlock): RelatedEntities {
    val start = columnNames.size
    val target = block.target
    return when (val view = target.viewDefinition) {
      null -> tableBlock(
        key = block.key,
        table = target
      )
      else -> {
        view.columnNames.mapTo(columnNames) { "${block.key}.$it" }
        val blocks = view.blocks ?: emptyArray()
        RelatedEntities(
          table = target,
          children = Array(blocks.size) { blocks[it]?.shifted(start) },
          startIndex = start,
          width = view.columnNames.size
        )
      }
    }
  }

  private fun tableBlock(key: String, table: Table<*>): RelatedEntities {
    val root = read(
      occurrence = key,
      table = table
    )
    val queue = ArrayDeque<Pair<String, RelatedEntities>>()
    queue.add(key to root)
    while (queue.isNotEmpty()) {
      val (occurrence, parent) = queue.removeFirst()
      val targets = facts.occurrences[occurrence.lowercase()]
      parent.table.tableRelationships.forEachIndexed { index, relationship ->
        if (relationship.read == ID_ONLY) {
          return@forEachIndexed
        }
        val target = relationship.target()
        val targetOccurrence = targets
          ?.get(index)
          ?.takeIf { name ->
            selects(
              occurrence = name,
              table = target
            )
          }
        when {
          targetOccurrence != null -> {
            val child = read(
              occurrence = targetOccurrence,
              table = target
            )
            parent.setChild(
              relationship = index,
              child = child
            )
            queue.add(targetOccurrence to child)
          }
          relationship.read == ALWAYS -> throw IllegalArgumentException(
            "Cannot create view '${facts.viewName}': $occurrence.${relationship.columnName} needs the columns of the " +
                "${target.name} its definition joins through it"
          )
        }
      }
    }
    return root
  }

  private fun read(occurrence: String, table: Table<*>): RelatedEntities {
    val start = columnNames.size
    val fields = table.selectedEntityFields()
    fields.mapTo(columnNames) { "$occurrence.${it.name}" }
    return RelatedEntities(
      table = table,
      children = arrayOfNulls(table.tableRelationships.size),
      startIndex = start,
      width = fields.size
    )
  }

  private fun selects(occurrence: String, table: Table<*>) = table
    .selectedEntityFields()
    .all { "$occurrence.${it.name}".lowercase() in facts.outputs }
}
