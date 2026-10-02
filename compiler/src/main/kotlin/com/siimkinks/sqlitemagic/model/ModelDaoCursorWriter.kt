package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.WriterTypes.CURSOR
import com.siimkinks.sqlitemagic.WriterTypes.MUTABLE_INT
import com.siimkinks.sqlitemagic.WriterTypes.SIMPLE_ARRAY_MAP
import com.siimkinks.sqlitemagic.writer.CursorAbsence
import com.siimkinks.sqlitemagic.writer.CursorPosition
import com.siimkinks.sqlitemagic.writer.CursorPositions
import com.siimkinks.sqlitemagic.writer.CursorReadNode
import com.siimkinks.sqlitemagic.writer.CursorReadProperty
import com.siimkinks.sqlitemagic.writer.CursorReadTree
import com.siimkinks.sqlitemagic.writer.CursorReadTreeWriter
import com.siimkinks.sqlitemagic.writer.databaseCursorGetter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal class ModelDaoCursorWriter(
  private val environment: Environment
) {
  private val tableReadLayout = TableReadLayout(environment.tableElements)

  fun write(
    table: TableElement,
    daoBuilder: TypeSpec.Builder
  ) {
    daoBuilder
      .addFunction(
        table.cursorConstructor(
          functionName = METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION,
          recursive = false
        )
      )
      .addFunction(
        table.selectedCursorConstructor(
          functionName = METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION,
          recursive = false
        )
      )
    if (table.hasRecursiveRelationships) {
      daoBuilder
        .addFunction(
          table.cursorConstructor(
            functionName = METHOD_FULL_OBJECT_FROM_CURSOR_POSITION,
            recursive = true
          )
        )
        .addFunction(
          table.selectedCursorConstructor(
            functionName = METHOD_FULL_OBJECT_FROM_CURSOR_POSITION,
            recursive = true
          )
        )
    }
  }

  internal fun completeProjectionTree(
    table: TableElement,
    recursive: Boolean,
    resolvePosition: (CompleteProjectionColumn) -> CursorPosition
  ): CursorReadTree = completeProjectionTree(
    table = table,
    recursive = recursive,
    relationshipPath = emptyList(),
    expandedOffset = 0,
    resolvePosition = resolvePosition
  )

  private fun completeProjectionTree(
    table: TableElement,
    recursive: Boolean,
    relationshipPath: List<String>,
    expandedOffset: Int,
    resolvePosition: (CompleteProjectionColumn) -> CursorPosition
  ): CursorReadTree {
    val columnIndexes = table.allColumns
      .withIndex()
      .associate { (index, column) ->
        column to resolvePosition(
          CompleteProjectionColumn(
            column = column,
            columnIndex = index,
            relationshipPath = relationshipPath,
            expandedOffset = expandedOffset + index
          )
        )
      }
    val relationshipTrees = linkedMapOf<ColumnElement, CursorReadTree>()
    var childOffset = expandedOffset + table.allColumns.size
    for (column in table.relationshipColumns) {
      val relationship = checkNotNull(column.relationship)
      val width = tableReadLayout.relationshipWidth(
        column = column,
        recursive = recursive
      )
      if (width > 0) {
        val target = checkNotNull(environment.tableElements[relationship.referencedTableTypeKey])
        relationshipTrees[column] = completeProjectionTree(
          table = target,
          recursive = recursive,
          relationshipPath = relationshipPath + column.columnName,
          expandedOffset = childOffset,
          resolvePosition = resolvePosition
        )
      }
      childOffset += width
    }
    return readTree(
      type = table.modelClassName,
      construction = table.construction,
      tableName = table.tableName,
      properties = table.properties,
      columnIndexes = columnIndexes,
      recursive = recursive,
      selection = null,
      relationshipTrees = relationshipTrees,
      completeProjection = true
    )
  }

  private fun TableElement.cursorConstructor(
    functionName: String,
    recursive: Boolean
  ): FunSpec {
    val positions = CursorPositions()
    val columnIndexes = allColumns
      .withIndex()
      .associate { (index, column) ->
        column to positions.positional(
          code = CodeBlock.of("thisTableOffset + %L", index),
          nullCheckName = "column${index}IsNull"
        )
      }
    val tree = readTree(
      type = modelClassName,
      construction = construction,
      tableName = tableName,
      properties = properties,
      columnIndexes = columnIndexes,
      recursive = recursive,
      selection = null
    )
    val result = positions.generate {
      CursorReadTreeWriter()
        .construct(tree)
    }
    val function = FunSpec
      .builder(functionName)
      .addParameter(name = "cursor", type = CURSOR)
      .addParameter(
        ParameterSpec
          .builder(name = "columnOffset", type = MUTABLE_INT)
          .defaultValue("%T()", MUTABLE_INT)
          .build()
      )
      .returns(modelClassName)
      .addStatement("val thisTableOffset = columnOffset.value")
      .addStatement("columnOffset.value += %L", allColumns.size)
    positions.addNullCheckDeclarations(function)
    return function
      .addStatement("return %L", result)
      .build()
  }

  private fun TableElement.selectedCursorConstructor(
    functionName: String,
    recursive: Boolean
  ): FunSpec {
    val positions = CursorPositions()
    val function = FunSpec
      .builder(functionName)
      .addParameter(name = "cursor", type = CURSOR)
      .addParameter(name = "columns", type = SIMPLE_ARRAY_MAP.parameterizedBy(STRING, INT))
      .addParameter(
        name = "tableGraphNodeNames",
        type = SIMPLE_ARRAY_MAP
          .parameterizedBy(STRING, STRING)
          .copy(nullable = true)
      )
      .addParameter(name = "nodeName", type = STRING)
      .returns(modelClassName.copy(nullable = true))
      .addStatement("val tableName = tableGraphNodeNames?.get(nodeName)")
      .beginControlFlow("if (tableName == null && nodeName.isNotEmpty())")
      .addStatement("return null")
      .endControlFlow()
      .addStatement("val effectiveTableName = tableName ?: %S", tableName)
      .addStatement("val thisTableOffset = columns[effectiveTableName]")
    val columnIndexes = allColumns
      .withIndex()
      .associate { (index, column) ->
        val tableOffset = when (index) {
          0 -> CodeBlock.of("thisTableOffset")
          else -> CodeBlock.of("thisTableOffset?.plus(%L)", index)
        }
        val lookup = CodeBlock.of(
          "%L ?: columns[%P]",
          tableOffset,
          $$"$effectiveTableName.$${column.columnName}"
        )
        column to positions.selected(
          function = function,
          indexName = "columnIndex$index",
          lookup = lookup,
          required = !column.isModelPathNullable,
          missingMessage = "Selected columns did not contain table \"${tableName}\" required column \"${column.columnName}\""
        )
      }
    val tree = readTree(
      type = modelClassName,
      construction = construction,
      tableName = tableName,
      properties = properties,
      columnIndexes = columnIndexes,
      recursive = recursive,
      selection = CursorSelection(
        columns = CodeBlock.of("%N", "columns"),
        tableGraphNodeNames = CodeBlock.of("%N", "tableGraphNodeNames"),
        nodeName = CodeBlock.of("%N", "nodeName")
      )
    )
    val result = positions.generate {
      CursorReadTreeWriter()
        .construct(tree)
    }
    positions.addNullCheckDeclarations(function)
    return function
      .addStatement("return %L", result)
      .build()
  }

  private fun readTree(
    type: TypeName,
    construction: ModelConstruction,
    tableName: String,
    properties: List<PropertyElement>,
    columnIndexes: Map<ColumnElement, CursorPosition>,
    recursive: Boolean,
    selection: CursorSelection?,
    relationshipTrees: Map<ColumnElement, CursorReadTree> = emptyMap(),
    completeProjection: Boolean = false
  ): CursorReadTree = CursorReadTree(
    type = type,
    construction = construction,
    nodes = properties.map { property ->
      when (property) {
        is ColumnPropertyElement -> columnNode(
          column = property.column,
          tableName = tableName,
          position = checkNotNull(columnIndexes[property.column]),
          recursive = recursive,
          selection = selection,
          nestedTree = relationshipTrees[property.column],
          completeProjection = completeProjection
        )
        is EmbeddedPropertyElement -> {
          val flattened = property.flattenedColumns()
          CursorReadNode.Embedded(
            source = CursorReadProperty.from(property),
            tree = readTree(
              type = property.deserializedType.typeName,
              construction = property.construction,
              tableName = tableName,
              properties = property.properties,
              columnIndexes = columnIndexes,
              recursive = recursive,
              selection = selection,
              relationshipTrees = relationshipTrees,
              completeProjection = completeProjection
            ),
            absence = CursorAbsence.TableEmbedded(
              positions = flattened.map { checkNotNull(columnIndexes[it]) },
              selected = selection != null
            ),
            skipWidthOnAbsent = when {
              selection == null && !completeProjection -> recursiveCursorOffset(
                columns = flattened,
                recursive = recursive
              )
              else -> 0
            },
            mutableValueGuarded = true
          )
        }
      }
    }
  )

  private fun columnNode(
    column: ColumnElement,
    tableName: String,
    position: CursorPosition,
    recursive: Boolean,
    selection: CursorSelection?,
    nestedTree: CursorReadTree?,
    completeProjection: Boolean
  ): CursorReadNode {
    val relationship = column.relationship ?: return CursorReadNode.Scalar(
      source = CursorReadProperty.from(column),
      position = position,
      storageType = column.sqlStorageType,
      transformer = column.transformer,
      serializedInputNullable = column.transformer?.serializedTypeCanBeNull == true,
      enclosingPathNullable = column.isModelPathNullable,
      requirePresentValue = completeProjection,
      missingMessage = "Selected columns did not contain table \"$tableName\" required column \"${column.columnName}\"",
      nullMessage = "Column \"${column.columnName}\" was NULL",
      mutableValueGuarded = true
    )
    val retrievesRelationship = relationship.isHandledRecursively &&
        (recursive || !relationship.canConstructWithOnlyId)
    val relationshipKind = if (column.isHandledRecursively) "recursive " else ""
    return CursorReadNode.PersistedRelationship(
      source = CursorReadProperty.from(column),
      position = position,
      read = {
        relationshipRead(
          column = column,
          position = position,
          recursive = recursive,
          selection = selection,
          retrievesRelationship = retrievesRelationship
        )
      },
      nestedTree = nestedTree,
      readCanBeNull = retrievesRelationship && selection != null && nestedTree == null,
      nullMessage = "Required ${relationshipKind}relationship \"${column.columnName}\" had a NULL ID",
      missingMessage = "Selected columns did not contain required relationship \"${column.columnName}\"",
      skipWidthOnAbsent = when {
        selection == null && !completeProjection && retrievesRelationship -> recursiveCursorOffset(
          columns = listOf(column),
          recursive = recursive
        )
        else -> 0
      },
      selected = selection != null || completeProjection
    )
  }

  private fun relationshipRead(
    column: ColumnElement,
    position: CursorPosition,
    recursive: Boolean,
    selection: CursorSelection?,
    retrievesRelationship: Boolean
  ): CodeBlock {
    val relationship = checkNotNull(column.relationship)
    val storedDatabaseId = when {
      !column.isNullable -> databaseCursorGetter(
        storageType = column.sqlStorageType,
        index = position.code
      )
      relationship.referencedIdIsNullable -> CodeBlock.of(
        "(if (%L) null else %L)",
        position.presentNullCheck(),
        databaseCursorGetter(
          storageType = column.sqlStorageType,
          index = position.code
        )
      )
      else -> databaseCursorGetter(
        storageType = column.sqlStorageType,
        index = position.code
      )
    }
    val databaseId = relationship.deserializedDeclaredIdValue(
      databaseValue = storedDatabaseId,
      databaseValueCanBeNull = relationship.databaseValueCanBeNull,
      databaseValueIsNonNull = !column.isNullable
    )
    val referencedTable = checkNotNull(environment.tableElements[relationship.referencedTableTypeKey])
    val daoClassName = referencedTable.generationNames.daoClassName
    return when {
      retrievesRelationship && selection != null -> CodeBlock.of(
        "%T.%N(cursor, %L, %L, %L + %S)",
        daoClassName,
        relationshipCursorReader(
          referencedTable = referencedTable,
          recursive = recursive
        ),
        selection.columns,
        selection.tableGraphNodeNames,
        selection.nodeName,
        column.columnName
      )
      retrievesRelationship -> CodeBlock.of(
        "%T.%N(cursor, columnOffset)",
        daoClassName,
        relationshipCursorReader(
          referencedTable = referencedTable,
          recursive = recursive
        )
      )
      relationship.canConstructWithOnlyId -> CodeBlock.of(
        "%T.%N(%L)",
        daoClassName,
        METHOD_NEW_INSTANCE_WITH_ONLY_ID,
        databaseId
      )
      else -> CodeBlock.of(
        "%T.%N(cursor, %L)",
        daoClassName,
        METHOD_FULL_OBJECT_FROM_CURSOR_POSITION,
        position.code
      )
    }
  }

  private fun relationshipCursorReader(
    referencedTable: TableElement,
    recursive: Boolean
  ) = when {
    recursive && referencedTable.hasRecursiveRelationships -> METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
    else -> METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
  }

  private fun recursiveCursorOffset(
    columns: List<ColumnElement>,
    recursive: Boolean
  ): Int = columns.sumOf { column ->
    when {
      !column.requiresRecursiveCursorOffset(recursive) -> 0
      else -> {
        val relationship = checkNotNull(column.relationship)
        val referencedTable = checkNotNull(environment.tableElements[relationship.referencedTableTypeKey])
        tableReadLayout.width(
          table = referencedTable,
          recursive = recursive
        )
      }
    }
  }

  private fun ColumnElement.requiresRecursiveCursorOffset(
    recursive: Boolean
  ) = when (val relationship = this.relationship) {
    null -> false
    else -> relationship.isHandledRecursively && (recursive || !relationship.canConstructWithOnlyId)
  }
}

private data class CursorSelection(
  val columns: CodeBlock,
  val tableGraphNodeNames: CodeBlock,
  val nodeName: CodeBlock
)

internal data class CompleteProjectionColumn(
  val column: ColumnElement,
  val columnIndex: Int,
  val relationshipPath: List<String>,
  val expandedOffset: Int
)
