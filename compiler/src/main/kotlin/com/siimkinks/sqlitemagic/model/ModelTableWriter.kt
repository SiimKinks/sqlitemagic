package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.SqlAffinity.INTEGER
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.ENCODING_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE_READ_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.PARAMETER_ENCODING_FACTORY
import com.siimkinks.sqlitemagic.WriterTypes.RELATIONSHIP_READ
import com.siimkinks.sqlitemagic.WriterTypes.SQL_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.SQL_STORAGE
import com.siimkinks.sqlitemagic.WriterTypes.TABLE_RELATIONSHIP
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE_COLUMN
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.siimkinks.sqlitemagic.writer.ReadTableStructureWriter
import com.siimkinks.sqlitemagic.writer.entityProperties
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec

internal class ModelTableWriter(
  private val environment: Environment
) : ModelWriter {
  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    readTableStructureWriter(table)
      .write(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles,
        fileProperties = table.allColumns.mapNotNull { column ->
          column.relationshipBackedId?.let { relationship ->
            relationshipBackedIdType(
              column = column,
              relationship = relationship
            )
          }
        }
      )
  }

  private fun readTableStructureWriter(table: TableElement) = ReadTableStructureWriter(
    names = table.generationNames,
    modelClassName = table.modelClassName,
    isPublic = table.isPublic,
    constructorArguments = buildList {
      add(namedArgument("name", "%S", table.tableName))
      add(namedArgument("alias", "%N", VARIABLE_ALIAS))
      if (table.schema == TEMPORARY) {
        add(namedArgument("temporary", "true"))
      }
    },
    columns = table.allColumns.map {
      columnProperty(
        table = table,
        column = it
      )
    },
    readsRelated = table.hasRecursiveRelationships,
    relationships = table.relationshipColumns
      .takeIf(List<ColumnElement>::isNotEmpty)
      ?.let(::relationships),
    partialRead = ReadTableStructureWriter.PartialRead(
      entityProperties = entityProperties(
        modelName = table.modelName,
        properties = table.allColumns,
        partialRequirements = table.partialRequirements
      ),
      readsRelated = table.hasRecursiveRelationships
    )
  )

  private fun columnProperty(
    table: TableElement,
    column: ColumnElement
  ): PropertySpec {
    val relationship = column.relationship
    val relationshipBackedId = column.relationshipBackedId
    return when {
      relationshipBackedId != null -> relationshipBackedIdProperty(
        table = table,
        column = column,
        relationship = relationshipBackedId
      )
      relationship == null -> scalarColumnProperty(
        table = table,
        column = column,
        value = column
      )
      else -> scalarColumnProperty(
        table = table,
        column = column,
        value = column.copy(
          deserializedType = relationship.referencedIdType,
          transformer = relationship.referencedIdTransformer,
          relationship = null
        )
      )
    }
  }

  private fun scalarColumnProperty(
    table: TableElement,
    column: ColumnElement,
    value: ColumnElement
  ) = scalarFieldProperty(
    ownerType = table.modelClassName,
    fieldClass = column.fieldClass,
    fieldName = column.fieldName,
    columnName = column.columnName,
    deserializedType = value.deserializedType.typeName,
    sqlStorageType = value.sqlStorageType,
    transformer = value.transformer,
    isSchemaNullable = value.isSchemaNullable,
    isModelPathNullable = value.isModelPathNullable,
    sqlRequired = value.isId && (value.sqlStorageType.affinity == INTEGER || WITHOUT_ROWID in table.options)
  )

  /** A relationship whose referenced ID is itself a relationship has the ID-only referenced model as its value. */
  private fun relationshipBackedIdProperty(
    table: TableElement,
    column: ColumnElement,
    relationship: RelationshipElement
  ): PropertySpec {
    val idType = relationship.nullableIdType
    return PropertySpec
      .builder(
        name = column.fieldName,
        type = column.fieldClass.parameterizedBy(
          table.modelClassName,
          idType,
          SQL_NULLABLE,
          idType,
          idType,
          SQL_NULLABLE,
          idType
        )
      )
      .initializer(
        callCode(
          callee = CodeBlock.of("%T", column.fieldClass),
          arguments = listOf(
            namedArgument("table", "this"),
            namedArgument("name", "%S", column.columnName),
            namedArgument("nullness", "%T", SQL_NULLABLE),
            namedArgument(
              "parameterEncoding",
              "%N.%M()",
              column.relationshipBackedIdTypeName,
              PARAMETER_ENCODING_FACTORY
            )
          )
        )
      )
      .build()
  }

  private fun relationshipBackedIdType(column: ColumnElement, relationship: RelationshipElement): PropertySpec {
    val idType = relationship.nullableIdType
    val serializedType = relationship.referencedIdSerializedType.typeName.copy(nullable = true)
    return PropertySpec
      .builder(
        name = column.relationshipBackedIdTypeName,
        type = ENCODING_TYPE.parameterizedBy(idType, idType, SQL_NULLABLE, SQL_NULLABLE)
      )
      .addModifiers(PRIVATE)
      .initializer(
        callCode(
          callee = CodeBlock.of("%M", NULLABLE_READ_TYPE),
          arguments = listOf(
            namedArgument("storage", "%T.%N", SQL_STORAGE, column.sqlStorageType.name),
            namedArgument(
              "encode",
              "{ value: %T -> value?.let { %L } }",
              idType,
              relationship.serializedDeclaredIdValue(value = CodeBlock.of("it"))
            ),
            namedArgument(
              "decode",
              "{ dbValue: %T -> dbValue?.let { %L } }",
              serializedType,
              relationship.deserializedDeclaredIdValue(CodeBlock.of("it"))
            ),
            namedArgument("encodingNullness", "%T", SQL_NULLABLE)
          )
        )
      )
      .build()
  }
}

/** Targets resolve lazily, since tables can reference each other. */
private fun relationships(columns: List<ColumnElement>) = callCode(
  callee = CodeBlock.of("listOf"),
  arguments = columns.map { column ->
    val relationship = checkNotNull(column.relationship)
    callCode(
      callee = CodeBlock.of("%T", TABLE_RELATIONSHIP),
      arguments = listOf(
        namedArgument("columnName", "%S", column.columnName),
        namedArgument("target", "{ %L }", relationship.referencedTableNames.tableHandle),
        namedArgument("targetIdColumnName", "%S", relationship.referencedIdColumnName),
        namedArgument("nullable", "%L", column.isNullable),
        namedArgument("read", "%T.%L", RELATIONSHIP_READ, relationship.read.name)
      )
    )
  }
)

private val ColumnElement.relationshipBackedIdTypeName get() = "${fieldName}_TYPE"

private val ColumnElement.fieldClass
  get() = when {
    (isUnique || isId) && hasEntityKeyValue -> UNIQUE_COLUMN
    else -> COLUMN
  }

private val ColumnElement.relationshipBackedId
  get() = relationship?.takeIf { it.referencedIdRelationship != null }

private val RelationshipElement.nullableIdType get() = referencedIdType.typeName.copy(nullable = true)
