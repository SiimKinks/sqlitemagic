package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_IDENTITY_STATEMENT_BINDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_STATEMENT_BINDER
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo

internal fun FileSpec.writeModelSource(
  codeGenerator: CodeGenerator,
  originatingFiles: OriginatingFiles
) {
  toBuilder()
    .addFileComment("%L", GENERATION_COMMENT)
    .build()
    .writeTo(
      codeGenerator = codeGenerator,
      aggregating = !originatingFiles.isComplete,
      originatingKSFiles = originatingFiles.files
    )
}

internal fun TableElement.schemaSql(
  columnSchemas: List<String> = allColumns.map(ColumnElement::schemaSql)
): String {
  val createPrefix = when {
    TableOption.TEMPORARY in options -> "CREATE TEMPORARY TABLE IF NOT EXISTS"
    else -> "CREATE TABLE IF NOT EXISTS"
  }
  val columns = columnSchemas.joinToString(separator = ", ")
  val suffix = when {
    TableOption.WITHOUT_ROWID in options -> " WITHOUT ROWID"
    else -> ""
  }
  return "$createPrefix $tableName ($columns)$suffix"
}

internal fun ColumnElement.schemaSql() = buildString {
  append(columnName)
  append(' ')
  append(sqlStorageType.affinity.name)
  when {
    isId -> {
      append(" PRIMARY KEY")
      if (id?.isAutoIncrement == true) {
        append(" AUTOINCREMENT")
      }
    }
    isUnique -> append(" UNIQUE")
  }
  if (!isId && !isUnique) {
    append(" DEFAULT ")
    append(defaultValue)
  }
  relationship
    ?.takeIf(RelationshipElement::onDeleteCascade)
    ?.let { relationship ->
      append(" REFERENCES ")
      append(relationship.referencedTableName)
      append('(')
      append(relationship.referencedIdColumnName)
      append(')')
      append(" ON DELETE CASCADE")
    }
}

internal fun SqlStorageType.parserName(nullable: Boolean) = when (this) {
  SqlStorageType.BYTE_ARRAY -> "UNBOXED_BYTE_ARRAY_PARSER"
  SqlStorageType.BOXED_BYTE_ARRAY -> "BOXED_BYTE_ARRAY_PARSER"
  SqlStorageType.BYTE -> when {
    nullable -> "NULLABLE_BYTE_PARSER"
    else -> "BYTE_PARSER"
  }
  SqlStorageType.DOUBLE -> "DOUBLE_PARSER"
  SqlStorageType.FLOAT -> "FLOAT_PARSER"
  SqlStorageType.INT -> when {
    nullable -> "NULLABLE_INTEGER_PARSER"
    else -> "INTEGER_PARSER"
  }
  SqlStorageType.LONG -> when {
    nullable -> "NULLABLE_LONG_PARSER"
    else -> "LONG_PARSER"
  }
  SqlStorageType.SHORT -> when {
    nullable -> "NULLABLE_SHORT_PARSER"
    else -> "SHORT_PARSER"
  }
  SqlStorageType.STRING -> "STRING_PARSER"
}

internal fun TableElement.insertSql(): String {
  val columns = columnsForInsert
  return when {
    columns.isEmpty() -> "INSERT%s INTO $tableName DEFAULT VALUES"
    else -> {
      val names = columns.joinToString(separator = ", ", transform = ColumnElement::columnName)
      val values = List(columns.size) { "?" }.joinToString(separator = ", ")
      "INSERT%s INTO $tableName ($names) VALUES ($values)"
    }
  }
}

internal fun TableElement.updateSql(): String? {
  val identityColumn = idColumn ?: eligibleUniqueColumns.firstOrNull() ?: return null
  return updateSql(identityColumn)
}

internal fun TableElement.updateSql(identityColumn: ColumnElement): String {
  val columns = allColumns.filterNot { column ->
    column === identityColumn || column.isId
  }
  val setters = when {
    columns.isEmpty() -> "${identityColumn.columnName}=${identityColumn.columnName}"
    else -> columns.joinToString(separator = ", ") { "${it.columnName}=?" }
  }
  return "UPDATE%s $tableName SET $setters WHERE ${identityColumn.columnName}=?"
}

internal fun TableElement.readExpression(
  column: ColumnElement,
  root: String = "entity"
): CodeBlock {
  val nullableSegments = mutableSetOf<Int>()
  fun collect(
    properties: List<PropertyElement>,
    depth: Int
  ) {
    for (property in properties) {
      if (property.access.path.segments == column.access.path.segments.take(depth + 1)) {
        if (property.isNullable) {
          nullableSegments += depth
        }
        if (property is EmbeddedPropertyElement) {
          collect(
            properties = property.properties,
            depth = depth + 1
          )
        }
        return
      }
    }
  }
  collect(
    properties = properties,
    depth = 0
  )
  return CodeBlock
    .builder()
    .add("%N", root)
    .apply {
      column.access.path.segments.forEachIndexed { index, segment ->
        add(
          when {
            index - 1 in nullableSegments -> "?.%N"
            else -> ".%N"
          },
          segment
        )
      }
    }
    .build()
}

internal fun TableElement.serializedReadExpression(column: ColumnElement): CodeBlock {
  var expression = readExpression(column)
  column.relationship?.let { relationship ->
    expression = expression.appendPropertyPath(
      path = relationship.referencedIdProperty,
      nullableReceiver = column.isModelPathNullable
    )
    expression = relationship.serializedDeclaredIdValue(
      value = expression,
      valueCanBeNull = column.isModelPathNullable || relationship.referencedIdIsNullable
    )
  }
  column.transformer?.let { transformer ->
    expression = when {
      column.isSchemaNullable -> CodeBlock.of(
        "%L?.let(%L)",
        expression,
        transformer.objectToDbValueMethod.callableReference()
      )
      else -> transformer.serializedValueGetter(expression)
    }
  }
  return expression
}

internal fun RelationshipElement.serializedDeclaredIdValue(
  value: CodeBlock,
  valueCanBeNull: Boolean = false
): CodeBlock {
  val nestedRelationship = referencedIdRelationship
  if (nestedRelationship != null) {
    val nestedValue = value.appendPropertyPath(
      path = nestedRelationship.referencedIdProperty,
      nullableReceiver = valueCanBeNull
    )
    return nestedRelationship.serializedDeclaredIdValue(
      value = nestedValue,
      valueCanBeNull = valueCanBeNull || nestedRelationship.referencedIdIsNullable
    )
  }
  val transformer = referencedIdTransformer ?: return value
  return when {
    valueCanBeNull -> CodeBlock.of(
      "%L?.let(%L)",
      value,
      transformer.objectToDbValueMethod.callableReference()
    )
    else -> transformer.serializedValueGetter(value)
  }
}

internal fun RelationshipElement.deserializedDeclaredIdValue(
  databaseValue: CodeBlock,
  databaseValueCanBeNull: Boolean = false
): CodeBlock {
  if (databaseValueCanBeNull && referencedIdType.typeName.isNullable) {
    return CodeBlock.of(
      "%L?.let { %L }",
      databaseValue,
      deserializedDeclaredIdValue(
        databaseValue = CodeBlock.of("it")
      )
    )
  }
  val nestedRelationship = referencedIdRelationship
  if (nestedRelationship != null) {
    return CodeBlock.of(
      "%T.%N(%L)",
      ModelGenerationNames(
        packageName = nestedRelationship.referencedTableType.typeName
          .let { checkNotNull(it as? ClassName).packageName },
        artifactStem = nestedRelationship.referencedTableArtifactStem
      ).daoClassName,
      METHOD_NEW_INSTANCE_WITH_ONLY_ID,
      nestedRelationship.deserializedDeclaredIdValue(
        databaseValue = databaseValue,
        databaseValueCanBeNull = databaseValueCanBeNull
      )
    )
  }
  return referencedIdTransformer
    ?.deserializedValueGetter(databaseValue)
    ?: databaseValue
}

internal fun CodeBlock.appendPropertyPath(
  path: PropertyPath,
  nullableReceiver: Boolean
) = toBuilder()
  .apply {
    path.segments.forEach { segment ->
      add(
        when {
          nullableReceiver -> "?.%N"
          else -> ".%N"
        },
        segment
      )
    }
  }
  .build()


internal fun TableElement.statementBinderType() = when {
  supportsIdentityOperations -> ENTITY_IDENTITY_STATEMENT_BINDER
  else -> ENTITY_STATEMENT_BINDER
}.parameterizedBy(modelClassName)

internal fun ColumnElement.databaseWriteValue(value: CodeBlock) = when (sqlStorageType) {
  SqlStorageType.BOXED_BYTE_ARRAY -> CodeBlock.of("%L.toByteArray()", value)
  SqlStorageType.BYTE -> CodeBlock.of("byteArrayOf(%L)", value)
  else -> value
}
