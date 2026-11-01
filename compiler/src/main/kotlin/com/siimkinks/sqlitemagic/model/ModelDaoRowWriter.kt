package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.DEEP
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.writer.ColumnPosition
import com.siimkinks.sqlitemagic.writer.RowReadNode
import com.siimkinks.sqlitemagic.writer.RowReadNode.Embedded
import com.siimkinks.sqlitemagic.writer.RowReadNode.PersistedRelationship
import com.siimkinks.sqlitemagic.writer.RowReadNode.Related
import com.siimkinks.sqlitemagic.writer.RowReadNode.Scalar
import com.siimkinks.sqlitemagic.writer.RowReadTree
import com.siimkinks.sqlitemagic.writer.databaseValueGetter
import com.siimkinks.sqlitemagic.writer.selectedPositionsReader
import com.siimkinks.sqlitemagic.writer.startIndexReader
import com.siimkinks.sqlitemagic.writer.toRowReadProperty
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

internal fun rowReaders(table: TableElement) = listOf(
  startIndexReader(
    returnType = table.modelClassName,
    readsRelated = table.hasRecursiveRelationships,
    columns = table.allColumns,
    tree = { positions ->
      readTree(
        type = table.modelClassName,
        construction = table.construction,
        properties = table.properties,
        positions = positions,
        columnNode = table::persistedNode
      )
    }
  ),
  selectedPositionsReader(
    returnType = table.modelClassName,
    readsRelated = table.hasRecursiveRelationships,
    columns = table.allColumns,
    mayBeMissing = { !table.isAlwaysSelected(it) },
    tree = { positions ->
      readTree(
        type = table.modelClassName,
        construction = table.construction,
        properties = table.properties,
        positions = positions,
        columnNode = table::selectedNode
      )
    }
  )
)

/** Required columns are always selected, since the runtime checks them before SQL. */
private fun TableElement.isAlwaysSelected(column: ColumnElement) = column in partialRequiredColumns &&
    (column.relationship == null || column.relationship.read == ID_ONLY)

private fun readTree(
  type: TypeName,
  construction: ModelConstruction,
  properties: List<PropertyElement>,
  positions: Map<ColumnElement, ColumnPosition>,
  columnNode: (ColumnElement, ColumnPosition) -> RowReadNode
): RowReadTree = RowReadTree(
  type = type,
  construction = construction,
  nodes = properties.map { property ->
    when (property) {
      is ColumnPropertyElement -> columnNode(property.column, positions.getValue(property.column))
      is EmbeddedPropertyElement -> Embedded(
        source = property.toRowReadProperty(),
        tree = readTree(
          type = property.deserializedType.typeName,
          construction = property.construction,
          properties = property.properties,
          positions = positions,
          columnNode = columnNode
        ),
        positions = property
          .flattenedColumns()
          .map(positions::getValue)
      )
    }
  }
)

private fun TableElement.persistedNode(
  column: ColumnElement,
  position: ColumnPosition
) = when (val relationship = column.relationship) {
  null -> scalarNode(
    column = column,
    position = position
  )
  else -> PersistedRelationship(
    source = column.toRowReadProperty(),
    position = position,
    read = relationshipValue(
      column = column,
      relationship = relationship,
      position = position,
      relationshipIndex = relationshipColumns.indexOf(column)
    ),
    isSqlNullable = column.isSqlNullable,
    nullMessage = column.relationshipNullMessage
  )
}

private fun TableElement.selectedNode(
  column: ColumnElement,
  position: ColumnPosition
) = when (val relationship = column.relationship) {
  null -> scalarNode(
    column = column,
    position = position
  )
  else -> Related(
    source = column.toRowReadProperty(),
    read = selectedRelationshipValue(
      column = column,
      relationship = relationship,
      position = position,
      relationshipIndex = relationshipColumns.indexOf(column)
    )
  )
}

private fun TableElement.scalarNode(
  column: ColumnElement,
  position: ColumnPosition
) = Scalar(
  source = column.toRowReadProperty(),
  position = position,
  storageType = column.sqlStorageType,
  transformer = column.transformer,
  isSqlNullable = column.isSqlNullable,
  requiresPresentValue = column.isModelPathNullable && column.transformer?.decoderInputNullable != true,
  missingMessage = """Selected columns did not contain table "$tableName" required column "${column.columnName}"""",
  nullMessage = """Column "${column.columnName}" was NULL"""
)

private fun relationshipValue(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: ColumnPosition,
  relationshipIndex: Int
): CodeBlock {
  val targetType = relationship.referencedTableType.typeName
  return when (relationship.read) {
    ID_ONLY -> idOnlyRelationship(
      column = column,
      relationship = relationship,
      position = position
    )
    DEEP -> CodeBlock.of(
      "(related.read(statement, %L) as %T?) ?: %L",
      relationshipIndex,
      targetType,
      idOnlyRelationship(
        column = column,
        relationship = relationship,
        position = position
      )
    )
    ALWAYS -> when {
      column.isNullable -> CodeBlock.of("related.read(statement, %L) as %T?", relationshipIndex, targetType)
      else -> CodeBlock.of(
        "(related.read(statement, %L) as %T?) ?: throw %T(%S)",
        relationshipIndex,
        targetType,
        SQL_EXCEPTION,
        column.missingRelationshipMessage
      )
    }
  }
}

private fun selectedRelationshipValue(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: ColumnPosition,
  relationshipIndex: Int
): CodeBlock {
  val selected = CodeBlock.of(
    "(related.readSelected(statement, %L) as %T?)",
    relationshipIndex,
    relationship.referencedTableType.typeName
  )
  return when (relationship.read) {
    ID_ONLY -> storedRelationship(
      column = column,
      relationship = relationship,
      position = position
    )
    DEEP -> CodeBlock.of(
      "%L ?: %L",
      selected,
      storedRelationship(
        column = column,
        relationship = relationship,
        position = position
      )
    )
    ALWAYS -> when {
      column.isNullable -> selected
      else -> CodeBlock.of(
        "%L ?: throw %T(if (%L) %S else %S)",
        selected,
        SQL_EXCEPTION,
        position.nullCheck().condition,
        column.relationshipNullMessage,
        column.missingRelationshipMessage
      )
    }
  }
}

private fun storedRelationship(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: ColumnPosition
): CodeBlock {
  val idOnly = idOnlyRelationship(
    column = column,
    relationship = relationship,
    position = position
  )
  return when {
    column.isNullable -> CodeBlock.of("(if (%L) null else %L)", position.nullCheck().condition, idOnly)
    else -> CodeBlock.of(
      "(if (%L) throw %T(%S) else %L)",
      position.nullCheck().condition,
      SQL_EXCEPTION,
      column.relationshipNullMessage,
      idOnly
    )
  }
}

/** Every caller has excluded a NULL ID. */
private fun idOnlyRelationship(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: ColumnPosition
) = CodeBlock.of(
  "%T.%N(%L)",
  relationship.referencedTableNames.daoClassName,
  METHOD_NEW_INSTANCE_WITH_ONLY_ID,
  relationship.deserializedDeclaredIdValue(
    databaseValueGetter(
      storageType = column.sqlStorageType,
      index = position.code
    )
  )
)

private val ColumnElement.relationshipNullMessage
  get() = when {
    isHandledRecursively -> """Required recursive relationship "$columnName" had a NULL ID"""
    else -> """Required relationship "$columnName" had a NULL ID"""
  }

private val ColumnElement.missingRelationshipMessage
  get() = when {
    isHandledRecursively -> """Required recursive relationship "$columnName" referenced a missing row"""
    else -> """Required relationship "$columnName" referenced a missing row"""
  }
