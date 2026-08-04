package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.CURSOR
import com.siimkinks.sqlitemagic.WriterTypes.MUTABLE_INT
import com.siimkinks.sqlitemagic.WriterTypes.SIMPLE_ARRAY_MAP
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode

internal class ModelDaoCursorWriter(
  private val environment: Environment
) {
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

  private fun TableElement.cursorConstructor(
    functionName: String,
    recursive: Boolean
  ): FunSpec {
    val cursorNullChecks = CursorNullChecks()
    val columnIndexes = allColumns
      .mapIndexed { index, column ->
        column to CursorPosition(
          code = CodeBlock.of("thisTableOffset + %L", index),
          mayBeMissing = false,
          nullCheckName = "column${index}IsNull",
          nullChecks = cursorNullChecks
        )
      }
      .toMap()
    val construction = cursorNullChecks.generate {
      constructionCode(
        type = modelClassName,
        construction = construction,
        tableName = tableName,
        properties = properties,
        columnIndexes = columnIndexes,
        recursive = recursive,
        selection = null
      )
    }
    return FunSpec
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
      .addCursorNullCheckDeclarations(cursorNullChecks)
      .addStatement("return %L", construction)
      .build()
  }

  private fun TableElement.selectedCursorConstructor(
    functionName: String,
    recursive: Boolean
  ): FunSpec {
    val cursorNullChecks = CursorNullChecks()
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
      .mapIndexed { index, column ->
        val indexName = "columnIndex$index"
        val tableOffset = when (index) {
          0 -> CodeBlock.of("thisTableOffset")
          else -> CodeBlock.of("thisTableOffset?.plus(%L)", index)
        }
        val lookup = CodeBlock.of(
          "%L ?: columns[%P]",
          tableOffset,
          $$"$effectiveTableName.$${column.columnName}"
        )
        when {
          column.isModelPathNullable -> function.addStatement("val %N = %L ?: -1", indexName, lookup)
          else -> function.addStatement(
            "val %N = %L ?: throw %T(%S)",
            indexName,
            lookup,
            SQL_EXCEPTION,
            "Selected columns did not contain table \"${tableName}\" required column \"${column.columnName}\""
          )
        }
        column to CursorPosition(
          code = CodeBlock.of("%N", indexName),
          mayBeMissing = column.isModelPathNullable,
          nullCheckName = "${indexName}IsNull",
          nullChecks = cursorNullChecks
        )
      }
      .toMap()
    val construction = cursorNullChecks.generate {
      constructionCode(
        type = modelClassName,
        construction = construction,
        tableName = tableName,
        properties = properties,
        columnIndexes = columnIndexes,
        recursive = recursive,
        selection = CursorSelection(
          columns = CodeBlock.of("columns"),
          tableGraphNodeNames = CodeBlock.of("tableGraphNodeNames"),
          nodeName = CodeBlock.of("nodeName")
        )
      )
    }
    return function
      .addCursorNullCheckDeclarations(cursorNullChecks)
      .addStatement("return %L", construction)
      .build()
  }

  private fun constructionCode(
    type: TypeName,
    construction: ModelConstruction,
    tableName: String,
    properties: List<PropertyElement>,
    columnIndexes: Map<ColumnElement, CursorPosition>,
    recursive: Boolean,
    selection: CursorSelection?
  ): CodeBlock = when (construction.strategy) {
    PRIMARY_CONSTRUCTOR -> CodeBlock
      .builder()
      .add("%T(\n", type.copy(nullable = false))
      .indent()
      .apply {
        properties.forEachIndexed { index, property ->
          add(
            "%N = %L",
            property.access.path.propertyName,
            propertyValueCode(
              property = property,
              tableName = tableName,
              columnIndexes = columnIndexes,
              recursive = recursive,
              selection = selection
            )
          )
          if (index != properties.lastIndex) {
            add(",")
          }
          add("\n")
        }
      }
      .unindent()
      .add(")")
      .build()
    MUTABLE_PROPERTIES -> CodeBlock
      .builder()
      .add("%T().apply {\n", type.copy(nullable = false))
      .indent()
      .apply {
        properties.forEach { property ->
          add(
            "%L\n",
            mutablePropertyAssignmentCode(
              property = property,
              tableName = tableName,
              columnIndexes = columnIndexes,
              recursive = recursive,
              selection = selection
            )
          )
        }
      }
      .unindent()
      .add("}")
      .build()
  }

  private fun mutablePropertyAssignmentCode(
    property: PropertyElement,
    tableName: String,
    columnIndexes: Map<ColumnElement, CursorPosition>,
    recursive: Boolean,
    selection: CursorSelection?
  ): CodeBlock {
    val nullableCheck = mutablePropertyNullCheck(
      property = property,
      columnIndexes = columnIndexes,
      selection = selection
    )
    val value = propertyValueCode(
      property = property,
      tableName = tableName,
      columnIndexes = columnIndexes,
      recursive = recursive,
      selection = selection,
      nullableValueGuarded = nullableCheck != null
    )
    val assignment = CodeBlock.of("this.%N = %L", property.access.path.propertyName, value)
    val skippedValue = when {
      nullableCheck == null || selection != null -> null
      else -> property
        .flattenedColumns()
        .takeIf { it.any { column -> column.requiresRecursiveCursorOffset(recursive) } }
        ?.let { skippedColumns ->
          skippedRecursiveValue(
            columns = skippedColumns,
            recursive = recursive
          )
        }
    }
    return when {
      nullableCheck == null -> assignment
      skippedValue != null -> CodeBlock.of("if (%L) %L else %L", nullableCheck, skippedValue, assignment)
      else -> CodeBlock.of("if (!%L) %L", nullableCheck, assignment)
    }
  }

  private fun mutablePropertyNullCheck(
    property: PropertyElement,
    columnIndexes: Map<ColumnElement, CursorPosition>,
    selection: CursorSelection?
  ): CodeBlock? = when (property) {
    is ColumnPropertyElement -> when {
      property.column.isNullable -> checkNotNull(columnIndexes[property.column]).nullCheck()
      else -> null
    }
    is EmbeddedPropertyElement -> when {
      property.isNullable -> nullableEmbeddedNullCheck(
        property = property,
        columnIndexes = columnIndexes,
        selection = selection
      )
      else -> null
    }
  }

  private fun nullableEmbeddedNullCheck(
    property: EmbeddedPropertyElement,
    columnIndexes: Map<ColumnElement, CursorPosition>,
    selection: CursorSelection?
  ): CodeBlock {
    val columnPositions = property.properties
      .flatMap(PropertyElement::flattenedColumns)
      .map { checkNotNull(columnIndexes[it]) }
    val nullCheck = columnPositions
      .map(CursorPosition::presentNullCheck)
      .joinToCode(separator = " && ")
    val missingCheck = selection?.let {
      columnPositions
        .map(CursorPosition::missingCheck)
        .joinToCode(separator = " && ")
    }
    return when (missingCheck) {
      null -> nullCheck
      else -> CodeBlock.of("(%L) || (%L)", missingCheck, nullCheck)
    }
  }

  private fun propertyValueCode(
    property: PropertyElement,
    tableName: String,
    columnIndexes: Map<ColumnElement, CursorPosition>,
    recursive: Boolean,
    selection: CursorSelection?,
    nullableValueGuarded: Boolean = false
  ): CodeBlock = when (property) {
    is ColumnPropertyElement -> columnValueCode(
      column = property.column,
      tableName = tableName,
      columnPosition = checkNotNull(columnIndexes[property.column]),
      recursive = recursive,
      selection = selection,
      nullableValueGuarded = nullableValueGuarded
    )
    is EmbeddedPropertyElement -> {
      val construction = constructionCode(
        type = property.deserializedType.typeName,
        construction = property.construction,
        tableName = tableName,
        properties = property.properties,
        columnIndexes = columnIndexes,
        recursive = recursive,
        selection = selection
      )
      when {
        property.isNullable && !nullableValueGuarded -> {
          val embeddedNullCheck = nullableEmbeddedNullCheck(
            property = property,
            columnIndexes = columnIndexes,
            selection = selection
          )
          when {
            selection == null -> CodeBlock.of(
              "if (%L) %L else %L",
              embeddedNullCheck,
              skippedRecursiveValue(
                columns = property.flattenedColumns(),
                recursive = recursive
              ),
              construction
            )
            else -> CodeBlock.of("if (%L) null else %L", embeddedNullCheck, construction)
          }
        }
        else -> construction
      }
    }
  }

  private fun columnValueCode(
    column: ColumnElement,
    tableName: String,
    columnPosition: CursorPosition,
    recursive: Boolean,
    selection: CursorSelection?,
    nullableValueGuarded: Boolean = false
  ): CodeBlock {
    val index = columnPosition.code
    val relationship = column.relationship ?: return columnValueFromDatabase(
      column = column,
      tableName = tableName,
      columnPosition = columnPosition,
      nullableValueGuarded = nullableValueGuarded
    )
    val storedDatabaseId = when {
      relationship.referencedIdIsNullable -> CodeBlock.of(
        "if (%L) null else %L",
        columnPosition.presentNullCheck(),
        databaseCursorGetter(
          column = column,
          index = index
        )
      )
      else -> databaseCursorGetter(
        column = column,
        index = index
      )
    }
    val databaseId = relationship.deserializedDeclaredIdValue(
      databaseValue = storedDatabaseId,
      databaseValueCanBeNull = relationship.referencedIdIsNullable ||
          relationship.serializedValueCanBeNull
    )
    val referencedTable = checkNotNull(
      environment.tableElements[relationship.referencedTableTypeKey]
    )
    val retrievesRelationship = relationship.isHandledRecursively &&
        (recursive || !relationship.canConstructWithOnlyId)
    val daoClassName = referencedTable.generationNames.daoClassName
    val idOnlyValue = CodeBlock.of("%T.%N(%L)", daoClassName, METHOD_NEW_INSTANCE_WITH_ONLY_ID, databaseId)
    val value = when {
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
      relationship.canConstructWithOnlyId -> idOnlyValue
      else -> CodeBlock.of("%T.%N(cursor, %L)", daoClassName, METHOD_FULL_OBJECT_FROM_CURSOR_POSITION, index)
    }
    val skippedRelationship = when {
      selection == null && retrievesRelationship -> skippedRecursiveValue(
        columns = listOf(column),
        recursive = recursive
      )
      else -> null
    }
    val result = when {
      !column.isNullable &&
          relationship.referencedIdIsNullable &&
          skippedRelationship != null -> CodeBlock.of(
        "if (%L) run { %L; %L } else %L",
        columnPosition.nullCheck(),
        skippedRelationship,
        idOnlyValue,
        value
      )
      !column.isNullable &&
          relationship.referencedIdIsNullable -> when {
        retrievesRelationship && selection != null -> CodeBlock.of(
          "(if (%L) %L else %L) ?: throw %T(%S)",
          columnPosition.nullCheck(),
          idOnlyValue,
          value,
          SQL_EXCEPTION,
          "Selected columns did not contain required relationship \"${column.columnName}\""
        )
        else -> CodeBlock.of(
          "if (%L) %L else %L",
          columnPosition.nullCheck(),
          idOnlyValue,
          value
        )
      }
      !nullableValueGuarded && column.isNullable && skippedRelationship != null -> CodeBlock.of(
        "if (%L) %L else %L",
        columnPosition.nullCheck(),
        skippedRelationship,
        value
      )
      !nullableValueGuarded && column.isNullable -> CodeBlock.of(
        "if (%L) null else %L",
        columnPosition.nullCheck(),
        value
      )
      retrievesRelationship && selection != null -> CodeBlock.of(
        "%L ?: throw %T(%S)",
        value,
        SQL_EXCEPTION,
        "Selected columns did not contain required relationship \"${column.columnName}\""
      )
      else -> value
    }
    return when {
      column.isModelPathNullable && !column.isNullable -> requiredNonNullColumnValue(
        column = column,
        tableName = tableName,
        columnPosition = columnPosition,
        value = result
      )
      else -> result
    }
  }

  private fun relationshipCursorReader(
    referencedTable: TableElement,
    recursive: Boolean
  ) = when {
    recursive && referencedTable.hasRecursiveRelationships -> METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
    else -> METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
  }

  private fun skippedRecursiveValue(
    columns: List<ColumnElement>,
    recursive: Boolean
  ): CodeBlock {
    val skippedColumns = columns.sumOf { column ->
      if (!column.requiresRecursiveCursorOffset(recursive)) {
        return@sumOf 0
      }
      val relationship = checkNotNull(column.relationship)
      val referencedTable = checkNotNull(
        environment.tableElements[relationship.referencedTableTypeKey]
      )
      cursorColumnCount(
        table = referencedTable,
        recursive = recursive
      )
    }
    return when {
      skippedColumns == 0 -> CodeBlock.of("null")
      else -> CodeBlock.of(
        "run { columnOffset.value += %L; null }",
        skippedColumns
      )
    }
  }

  private fun ColumnElement.requiresRecursiveCursorOffset(recursive: Boolean): Boolean =
    relationship?.let {
      it.isHandledRecursively &&
          (recursive || !it.canConstructWithOnlyId)
    } == true

  private fun cursorColumnCount(
    table: TableElement,
    recursive: Boolean
  ): Int = table.allColumns.size + table.relationshipColumns.sumOf { column ->
    val relationship = checkNotNull(column.relationship)
    when {
      !relationship.isHandledRecursively -> 0
      !recursive && relationship.canConstructWithOnlyId -> 0
      else -> cursorColumnCount(
        table = checkNotNull(
          environment.tableElements[relationship.referencedTableTypeKey]
        ),
        recursive = recursive
      )
    }
  }

  private fun columnValueFromDatabase(
    column: ColumnElement,
    tableName: String,
    columnPosition: CursorPosition,
    nullableValueGuarded: Boolean = false
  ): CodeBlock {
    val index = columnPosition.code
    val databaseValue = databaseCursorGetter(
      column = column,
      index = index
    )
    val value = column.transformer
      ?.deserializedValueGetter(
        when {
          column.transformer.serializedTypeCanBeNull -> CodeBlock.of(
            "if (%L) null else %L",
            columnPosition.presentNullCheck(),
            databaseValue
          )
          else -> databaseValue
        }
      )
      ?: databaseValue
    val result = when {
      column.isModelPathNullable && !column.isNullable -> requiredNonNullColumnValue(
        column = column,
        tableName = tableName,
        columnPosition = columnPosition,
        value = value
      )
      !nullableValueGuarded && column.isNullable -> CodeBlock.of(
        "if (%L) null else %L",
        columnPosition.nullCheck(),
        value
      )
      else -> value
    }
    return result
  }

  private fun requiredNonNullColumnValue(
    column: ColumnElement,
    tableName: String,
    columnPosition: CursorPosition,
    value: CodeBlock
  ): CodeBlock = when {
    columnPosition.mayBeMissing -> CodeBlock.of(
      "if (%L) throw %T(%S)\n" +
          "else if (%L) throw %T(%S)\n" +
          "else %L",
      columnPosition.missingCheck(),
      SQL_EXCEPTION,
      "Selected columns did not contain table \"$tableName\" required column \"${column.columnName}\"",
      columnPosition.presentNullCheck(),
      SQL_EXCEPTION,
      "Column \"${column.columnName}\" was NULL",
      value
    )
    else -> CodeBlock.of(
      "if (%L) throw %T(%S) else %L",
      columnPosition.presentNullCheck(),
      SQL_EXCEPTION,
      "Column \"${column.columnName}\" was NULL",
      value
    )
  }

  private fun databaseCursorGetter(
    column: ColumnElement,
    index: CodeBlock
  ) = when (column.sqlStorageType) {
    SqlStorageType.BYTE_ARRAY -> CodeBlock.of("cursor.getBlob(%L)", index)
    SqlStorageType.BOXED_BYTE_ARRAY -> CodeBlock.of("cursor.getBlob(%L).toTypedArray()", index)
    SqlStorageType.BYTE -> CodeBlock.of("cursor.getBlob(%L)[0]", index)
    SqlStorageType.DOUBLE -> CodeBlock.of("cursor.getDouble(%L)", index)
    SqlStorageType.FLOAT -> CodeBlock.of("cursor.getFloat(%L)", index)
    SqlStorageType.INT -> CodeBlock.of("cursor.getInt(%L)", index)
    SqlStorageType.LONG -> CodeBlock.of("cursor.getLong(%L)", index)
    SqlStorageType.SHORT -> CodeBlock.of("cursor.getShort(%L)", index)
    SqlStorageType.STRING -> CodeBlock.of("cursor.getString(%L)", index)
  }

}

private fun FunSpec.Builder.addCursorNullCheckDeclarations(
  cursorNullChecks: CursorNullChecks
) = apply {
  cursorNullChecks.declarations.forEach { declaration ->
    addStatement(
      "val %N = %L",
      declaration.name,
      when {
        declaration.mayBeMissing -> CodeBlock.of(
          "(%L >= 0 && cursor.isNull(%L))",
          declaration.code,
          declaration.code
        )
        else -> CodeBlock.of("cursor.isNull(%L)", declaration.code)
      }
    )
  }
}

private class CursorNullChecks {
  private val usages = linkedMapOf<CursorNullCheckKey, CursorNullCheckUsage>()
  private var prepared = false

  val declarations: List<CursorNullCheckDeclaration>
    get() = usages.values
      .filter { it.count > 1 }
      .map { usage ->
        CursorNullCheckDeclaration(
          name = usage.name,
          code = usage.code,
          mayBeMissing = usage.mayBeMissing
        )
      }

  fun generate(construction: () -> CodeBlock): CodeBlock {
    // The first pass counts repeated checks; the second pass can then reuse their generated locals.
    construction()
    prepared = true
    return construction()
  }

  fun nullCheck(position: CursorPosition): CodeBlock {
    val cached = cachedValue(position)
    return when {
      cached != null && position.mayBeMissing -> CodeBlock.of(
        "(%L < 0 || %L)",
        position.code,
        cached
      )
      cached != null -> cached
      position.mayBeMissing -> CodeBlock.of("(%L < 0 || cursor.isNull(%L))", position.code, position.code)
      else -> CodeBlock.of("cursor.isNull(%L)", position.code)
    }
  }

  fun presentNullCheck(position: CursorPosition): CodeBlock {
    val cached = cachedValue(position)
    return when {
      cached != null -> cached
      position.mayBeMissing -> CodeBlock.of("(%L >= 0 && cursor.isNull(%L))", position.code, position.code)
      else -> CodeBlock.of("cursor.isNull(%L)", position.code)
    }
  }

  private fun cachedValue(position: CursorPosition): CodeBlock? {
    val key = CursorNullCheckKey(
      code = position.code.toString(),
      mayBeMissing = position.mayBeMissing
    )
    val usage = usages.getOrPut(key) {
      CursorNullCheckUsage(
        name = position.nullCheckName,
        code = position.code,
        mayBeMissing = position.mayBeMissing
      )
    }
    if (!prepared) {
      usage.count++
    }
    return when {
      prepared && usage.count > 1 -> CodeBlock.of("%N", usage.name)
      else -> null
    }
  }
}

private data class CursorNullCheckKey(
  val code: String,
  val mayBeMissing: Boolean
)

private data class CursorNullCheckUsage(
  val name: String,
  val code: CodeBlock,
  val mayBeMissing: Boolean,
  var count: Int = 0
)

private data class CursorNullCheckDeclaration(
  val name: String,
  val code: CodeBlock,
  val mayBeMissing: Boolean
)

private data class CursorPosition(
  val code: CodeBlock,
  val mayBeMissing: Boolean,
  val nullCheckName: String,
  val nullChecks: CursorNullChecks
) {
  fun missingCheck() = when {
    mayBeMissing -> CodeBlock.of("%L < 0", code)
    else -> CodeBlock.of("false")
  }

  fun nullCheck() = nullChecks.nullCheck(this)

  fun presentNullCheck() = nullChecks.presentNullCheck(this)
}

private data class CursorSelection(
  val columns: CodeBlock,
  val tableGraphNodeNames: CodeBlock,
  val nodeName: CodeBlock
)
