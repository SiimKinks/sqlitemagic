package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.ExportedEntity
import com.siimkinks.sqlitemagic.NullableEntity
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.Selection
import com.siimkinks.sqlitemagic.entity
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.ViewBlock
import com.siimkinks.sqlitemagic.internal.ViewDefinition
import com.siimkinks.sqlitemagic.label
import com.siimkinks.sqlitemagic.requireUniqueExportedNames

internal fun compileViewDefinition(
  query: SelectQuery<*, *>,
  viewName: String,
  columns: List<String>,
  blocks: List<ViewBlock>
): ViewDefinition {
  val failure = "Cannot create view '$viewName'"
  val node = query.node
  require(node.connection == null) { "$failure: a view definition cannot use a connection" }
  val selection = node.selection
  selection.requireUniqueExportedNames(source = "$failure: its definition")
  val context = SqlRenderingContext(
    DefinitionFacts(
      viewName = viewName,
      exportedNames = selection.exportedNames
    )
  )
  node.render(context)
  requireOwnColumns(
    failure = failure,
    columns = columns,
    entities = selection.exportedEntities
  )
  val columnNames = ArrayList(columns)
  val blockReads = when {
    blocks.isEmpty() -> null
    else -> Array<RelatedEntities?>(blocks.size) { index ->
      blockRead(
        failure = failure,
        block = blocks[index],
        selection = selection,
        columnNames = columnNames
      )
    }
  }
  return ViewDefinition(
    sql = context.sql.toString(),
    queryDependencies = context.dependencies.build(),
    columnNames = columnNames.toTypedArray(),
    blocks = blockReads
  )
}

private fun requireOwnColumns(failure: String, columns: List<String>, entities: List<ExportedEntity>) {
  columns.forEach { column ->
    val owner = entities.firstOrNull { entity ->
      entity.export.names.any {
        it.equals(
          other = column,
          ignoreCase = true
        )
      }
    }
    require(owner == null) {
      "$failure: column $column is part of the selected ${owner?.entityLabel}; a scalar property reads a " +
          "column selected itself, so select it with an alias"
    }
  }
}

private fun blockRead(
  failure: String,
  block: ViewBlock,
  selection: Selection<*>,
  columnNames: MutableList<String>
): RelatedEntities {
  val target = block.target
  val entity = selection.exportedEntities.firstOrNull {
    it.export.key.equals(
      other = block.key,
      ignoreCase = true
    )
  }
  requireNotNull(entity) {
    val columnsOnly = target
      .selectedEntityFields()
      .all { field ->
        val name = "${block.key}.${field.name}"
        selection.exportedNames.any {
          it.equals(
            other = name,
            ignoreCase = true
          )
        }
      }
    val entityName = when {
      block.key.equals(
        other = target.name,
        ignoreCase = true
      ) -> "the ${target.name} entity"
      else -> "the ${target.name} entity of occurrence ${block.key}"
    }
    when {
      columnsOnly -> "$failure: ${block.path} reads an entity, and its definition selects only the columns of " +
          "${block.key}; select $entityName instead of its all()"
      else -> "$failure: ${block.path} reads an entity; select $entityName"
    }
  }
  val export = entity.export
  require(export.table.objectIdentity.normalizedKey == target.objectIdentity.normalizedKey) {
    "$failure: ${block.path} reads the ${target.name} entity, and its definition selects the " +
        "${export.table.name} entity as ${block.key}"
  }
  require(block.nullable || entity.value !is NullableEntity<*>) {
    "$failure: ${block.path} is not nullable, and its definition selects the ${entity.entityLabel} as nullable"
  }
  val read = export.read(columnNames.size)
  columnNames.addAll(export.names)
  return read
}

private val ExportedEntity.entityLabel get() = value.entity?.label
