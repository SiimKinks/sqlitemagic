package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.DEEP
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.writer.CursorPosition
import com.siimkinks.sqlitemagic.writer.CursorReadNode
import com.siimkinks.sqlitemagic.writer.CursorReadTree
import com.siimkinks.sqlitemagic.writer.cursorPositionReader
import com.siimkinks.sqlitemagic.writer.databaseCursorGetter
import com.siimkinks.sqlitemagic.writer.selectedPositionsReader
import com.siimkinks.sqlitemagic.writer.toCursorReadProperty
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

internal fun cursorReaders(table: TableElement) = listOf(
  cursorPositionReader(
    returnType = table.modelClassName,
    readsRelated = table.hasRecursiveRelationships,
    columns = table.allColumns,
    tree = { positions ->
      readTree(
        type = table.modelClassName,
        construction = table.construction,
        properties = table.properties,
        positions = positions,
        columnNode = { column, position ->
          table.persistedNode(
            column = column,
            position = position
          )
        }
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
        columnNode = { column, position ->
          table.selectedNode(
            column = column,
            position = position
          )
        }
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
  positions: Map<ColumnElement, CursorPosition>,
  columnNode: (ColumnElement, CursorPosition) -> CursorReadNode
): CursorReadTree = CursorReadTree(
  type = type,
  construction = construction,
  nodes = properties.map { property ->
    when (property) {
      is ColumnPropertyElement -> columnNode(property.column, positions.getValue(property.column))
      is EmbeddedPropertyElement -> CursorReadNode.Embedded(
        source = property.toCursorReadProperty(),
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
  position: CursorPosition
): CursorReadNode {
  val relationship = column.relationship ?: return scalarNode(
    column = column,
    position = position
  )
  return CursorReadNode.PersistedRelationship(
    source = column.toCursorReadProperty(),
    position = position,
    read = relationshipValue(
      column = column,
      relationship = relationship,
      position = position,
      relationshipIndex = relationshipColumns.indexOf(column)
    ),
    nullMessage = column.relationshipNullMessage
  )
}

private fun TableElement.selectedNode(
  column: ColumnElement,
  position: CursorPosition
): CursorReadNode {
  val relationship = column.relationship ?: return scalarNode(
    column = column,
    position = position
  )
  return CursorReadNode.Related(
    source = column.toCursorReadProperty(),
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
  position: CursorPosition
) = CursorReadNode.Scalar(
  source = column.toCursorReadProperty(),
  position = position,
  storageType = column.sqlStorageType,
  transformer = column.transformer,
  requiresPresentValue = column.isModelPathNullable,
  missingMessage = """Selected columns did not contain table "$tableName" required column "${column.columnName}"""",
  nullMessage = """Column "${column.columnName}" was NULL"""
)

private fun relationshipValue(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: CursorPosition,
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
      "(related.read(cursor, %L) as %T?) ?: %L",
      relationshipIndex,
      targetType,
      idOnlyRelationship(
        column = column,
        relationship = relationship,
        position = position
      )
    )
    ALWAYS -> CodeBlock.of("related.read(cursor, %L) as %T", relationshipIndex, targetType)
  }
}

private fun selectedRelationshipValue(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: CursorPosition,
  relationshipIndex: Int
): CodeBlock {
  val targetType = relationship.referencedTableType.typeName
  val required = !column.isNullable
  val idOnly = idOnlyRelationship(
    column = column,
    relationship = relationship,
    position = position
  )
  val nullFailure = CodeBlock.of("throw %T(%S)", SQL_EXCEPTION, column.relationshipNullMessage)
  val stored = when {
    required -> CodeBlock.of("(if (%L) %L else %L)", position.nullCheck().condition, nullFailure, idOnly)
    else -> CodeBlock.of("(if (%L) null else %L)", position.nullCheck().condition, idOnly)
  }
  val selected = CodeBlock.of("(related.readSelected(cursor, %L) as %T?)", relationshipIndex, targetType)
  return when (relationship.read) {
    ID_ONLY -> stored
    DEEP -> CodeBlock.of("%L ?: %L", selected, stored)
    ALWAYS -> when {
      required -> CodeBlock.of("%L ?: %L", selected, nullFailure)
      else -> selected
    }
  }
}

/** Every caller has excluded a NULL ID. */
private fun idOnlyRelationship(
  column: ColumnElement,
  relationship: RelationshipElement,
  position: CursorPosition
) = CodeBlock.of(
  "%T.%N(%L)",
  relationship.referencedTableNames.daoClassName,
  METHOD_NEW_INSTANCE_WITH_ONLY_ID,
  relationship.deserializedDeclaredIdValue(
    databaseCursorGetter(
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
