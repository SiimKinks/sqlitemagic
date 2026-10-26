package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_IDENTITY_STATEMENT_BINDER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_STATEMENT_BINDER
import com.siimkinks.sqlitemagic.WriterTypes.OPT_IN_INTERNAL_API
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.writeTo

internal fun FileSpec.Builder.writeModelSource(
  codeGenerator: CodeGenerator,
  originatingFiles: OriginatingFiles
) = addFileComment("%L", GENERATION_COMMENT)
  .addAnnotation(OPT_IN_INTERNAL_API)
  .build()
  .writeTo(
    codeGenerator = codeGenerator,
    aggregating = !originatingFiles.isComplete,
    originatingKSFiles = originatingFiles.files
  )

internal fun TableElement.schemaSql(
  columnSchemas: List<String> = allColumns.map(ColumnElement::schemaSql)
): String {
  val createPrefix = when {
    TEMPORARY in options -> "CREATE TEMPORARY TABLE IF NOT EXISTS"
    else -> "CREATE TABLE IF NOT EXISTS"
  }
  val columns = columnSchemas.joinToString(separator = ", ")
  val suffix = when {
    WITHOUT_ROWID in options -> " WITHOUT ROWID"
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
    else -> append(" DEFAULT $defaultValue")
  }
  relationship
    ?.takeIf(RelationshipElement::onDeleteCascade)
    ?.let { append(" REFERENCES ${it.referencedTableName}(${it.referencedIdColumnName}) ON DELETE CASCADE") }
}

internal fun TableElement.readExpression(column: ColumnElement): CodeBlock {
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
  return buildCodeBlock {
    add("entity")
    var nullableReceiver = false
    column.access.path.segments.forEachIndexed { index, segment ->
      if (index - 1 in nullableSegments) {
        nullableReceiver = true
      }
      add(
        when {
          nullableReceiver -> "?.%N"
          else -> ".%N"
        },
        segment
      )
    }
  }
}

internal fun TableElement.serializedReadExpression(
  column: ColumnElement,
  propertyReadExpression: CodeBlock = readExpression(column)
) = when {
  column.relationship != null -> column.relationship.serializedDeclaredIdValue(
    value = propertyReadExpression.appendPropertyPath(
      path = column.relationship.referencedIdProperty,
      nullableReceiver = column.isModelPathNullable
    ),
    valueCanBeNull = column.isModelPathNullable || column.relationship.referencedIdValueCanBeNull
  )
  column.transformer != null && column.isSchemaNullable -> CodeBlock.of(
    "%L?.let(%L)",
    propertyReadExpression,
    column.transformer.objectToDbValueMethod.callableReference()
  )
  column.transformer != null -> column.transformer.serializedValueGetter(propertyReadExpression)
  else -> propertyReadExpression
}

internal fun RelationshipElement.serializedDeclaredIdValue(
  value: CodeBlock,
  valueCanBeNull: Boolean = false
): CodeBlock = when (val nestedRelationship = referencedIdRelationship) {
  null -> when {
    referencedIdTransformer == null -> value
    valueCanBeNull -> CodeBlock.of(
      "%L?.let(%L)",
      value,
      referencedIdTransformer.objectToDbValueMethod.callableReference()
    )
    else -> referencedIdTransformer.serializedValueGetter(value)
  }
  else -> nestedRelationship.serializedDeclaredIdValue(
    value = value.appendPropertyPath(
      path = nestedRelationship.referencedIdProperty,
      nullableReceiver = valueCanBeNull
    ),
    valueCanBeNull = valueCanBeNull || nestedRelationship.referencedIdValueCanBeNull
  )
}

internal fun RelationshipElement.deserializedDeclaredIdValue(databaseValue: CodeBlock): CodeBlock =
  when (val nestedRelationship = referencedIdRelationship) {
    null -> referencedIdTransformer?.deserializedValueGetter(databaseValue) ?: databaseValue
    else -> CodeBlock.of(
      "%T.%N(%L)",
      nestedRelationship.referencedTableNames.daoClassName,
      METHOD_NEW_INSTANCE_WITH_ONLY_ID,
      nestedRelationship.deserializedDeclaredIdValue(databaseValue)
    )
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

/** The runtime passes only the table's identity column names, so the last one is the `else` branch. */
internal fun identityBranch(columns: List<ColumnElement>, index: Int) = when (index) {
  columns.lastIndex -> CodeBlock.of("else")
  else -> CodeBlock.of("%S", columns[index].columnName)
}
