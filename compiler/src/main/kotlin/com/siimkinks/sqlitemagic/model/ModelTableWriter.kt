package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_RELATIONSHIP_ID_ENCODINGS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.ENCODING_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.FTS5_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE_READ_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.PARAMETER_ENCODING_FACTORY
import com.siimkinks.sqlitemagic.WriterTypes.RELATIONSHIP_READ
import com.siimkinks.sqlitemagic.WriterTypes.SQL_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.SQL_STORAGE
import com.siimkinks.sqlitemagic.WriterTypes.TABLE_RELATIONSHIP
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.nullnessType
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.fulltext.FullTextIndexElement
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.siimkinks.sqlitemagic.writer.FullTextTableStructureWriter
import com.siimkinks.sqlitemagic.writer.ReadTableStructureWriter
import com.siimkinks.sqlitemagic.writer.ReadTableStructureWriter.PartialRead
import com.siimkinks.sqlitemagic.writer.databaseValueGetter
import com.siimkinks.sqlitemagic.writer.entityProperties
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

internal class ModelTableWriter(
  private val environment: Environment
) : ModelWriter {
  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    readTableStructureWriter(table)
      .write(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
    table.fullTextIndexes.forEach { index ->
      fullTextTableStructureWriter(
        table = table,
        index = index
      )
        .write(
          codeGenerator = environment.codeGenerator,
          originatingFiles = originatingFiles
        )
    }
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
      if (WITHOUT_ROWID in table.options) {
        add(namedArgument("withoutRowId", "true"))
      }
    },
    columns = table.allColumns.map { column ->
      columnProperty(
        column = column,
        field = FieldTarget(
          fieldClass = column.fieldClass,
          ownerTypeArguments = listOf(table.modelClassName),
          table = CodeBlock.of("this"),
          encodingsClassName = table.generationNames.tableClassName,
          fieldName = column.fieldName,
          columnName = column.columnName
        )
      )
    },
    readsRelated = table.hasRecursiveRelationships,
    relationships = table.relationshipColumns
      .takeIf(List<ColumnElement>::isNotEmpty)
      ?.let(::relationships),
    partialRead = PartialRead(
      entityProperties = entityProperties(
        modelName = table.modelName,
        properties = table.allColumns,
        partialRequirements = table.partialRequirements
      ),
      readsRelated = table.hasRecursiveRelationships
    ),
    relationshipIdEncodings = relationshipIdEncodings(table.allColumns)
  )

  private fun fullTextTableStructureWriter(
    table: TableElement,
    index: FullTextIndexElement
  ): FullTextTableStructureWriter {
    val className = index.tableClassName
    val columnsByName = table.allColumns.associateBy(ColumnElement::columnName)
    val members = index.columns.map { columnsByName.getValue(it.columnName) }
    val rowIdColumn = columnsByName.getValue(index.rowIdColumnName)
    fun field(
      column: ColumnElement,
      fieldClass: ClassName,
      ownerTypeArguments: List<TypeName>,
      fieldName: String,
      columnName: String,
      position: Int?
    ) = columnProperty(
      column = column,
      field = FieldTarget(
        fieldClass = fieldClass,
        ownerTypeArguments = ownerTypeArguments,
        table = CodeBlock.of("fieldTable"),
        encodingsClassName = className,
        fieldName = fieldName,
        columnName = columnName,
        position = position
      )
    )
    return FullTextTableStructureWriter(
      className = className,
      fieldName = index.fieldName,
      isPublic = table.isPublic,
      constructorArguments = listOf(
        namedArgument("name", "%S", index.name),
        namedArgument("alias", "%N", VARIABLE_ALIAS),
        namedArgument("contentTable", "%L", table.generationNames.tableHandle),
        namedArgument("contentRowIdColumnName", "%S", index.rowIdColumnName)
      ),
      columns = members.mapIndexed { position, column ->
        field(
          column = column,
          fieldClass = FTS5_COLUMN,
          ownerTypeArguments = emptyList(),
          fieldName = column.fieldName,
          columnName = column.columnName,
          position = position
        )
      } + field(
        column = rowIdColumn,
        fieldClass = COLUMN,
        ownerTypeArguments = listOf(NOTHING),
        fieldName = FULL_TEXT_ROW_ID_FIELD,
        columnName = FULL_TEXT_ROW_ID_COLUMN,
        position = null
      ),
      relationshipIdEncodings = relationshipIdEncodings((members + rowIdColumn).distinct())
    )
  }

  private fun columnProperty(
    column: ColumnElement,
    field: FieldTarget
  ): PropertySpec {
    val relationship = column.relationship
    val relationshipBackedId = column.relationshipBackedId
    return when {
      relationshipBackedId != null -> relationshipBackedIdProperty(
        column = column,
        field = field,
        relationship = relationshipBackedId
      )
      relationship == null -> scalarColumnProperty(
        column = column,
        field = field,
        value = column
      )
      else -> scalarColumnProperty(
        column = column,
        field = field,
        value = column.copy(
          deserializedType = relationship.referencedIdType,
          transformer = relationship.referencedIdTransformer,
          relationship = null
        )
      )
    }
  }

  private fun scalarColumnProperty(
    column: ColumnElement,
    field: FieldTarget,
    value: ColumnElement
  ) = scalarFieldProperty(
    ownerTypeArguments = field.ownerTypeArguments,
    fieldClass = field.fieldClass,
    tableArgument = field.table,
    fieldName = field.fieldName,
    columnName = field.columnName,
    position = field.position,
    propertyName = column.access.path.displayName,
    deserializedType = value.deserializedType.typeName,
    sqlStorageType = value.sqlStorageType,
    transformer = value.transformer,
    isSqlNullable = column.isSqlNullable,
    isModelPathNullable = value.isModelPathNullable,
    checksRequiredNull = false
  )

  /** A relationship whose referenced ID is itself a relationship has the ID-only referenced model as its value. */
  private fun relationshipBackedIdProperty(
    column: ColumnElement,
    field: FieldTarget,
    relationship: RelationshipElement
  ): PropertySpec {
    val idType = relationship.nullableIdType
    val nullness = nullnessType(nullable = column.isSqlNullable)
    val requiredRead = when {
      column.isSqlNullable -> null
      else -> namedArgument(
        "selectedRead",
        "{ statement, index -> %L }",
        relationship.deserializedDeclaredIdValue(
          databaseValueGetter(
            storageType = column.sqlStorageType,
            index = CodeBlock.of("index")
          )
        )
      )
    }
    return PropertySpec
      .builder(
        name = field.fieldName,
        type = field.fieldClass.parameterizedBy(
          field.ownerTypeArguments + listOf(
            idType,
            nullness,
            idType.copy(nullable = column.isSqlNullable),
            idType.copy(nullable = column.isModelPathNullable),
            SQL_NULLABLE,
            idType
          )
        )
      )
      .initializer(
        callCode(
          callee = CodeBlock.of("%T", field.fieldClass),
          arguments = listOfNotNull(
            namedArgument("table", "%L", field.table),
            namedArgument("name", "%S", field.columnName),
            field.position?.let { namedArgument("position", "%L", it) },
            namedArgument("nullness", "%T", nullness),
            namedArgument(
              "parameterEncoding",
              "%T.%N.%M()",
              field.encodingsClassName.nestedClass(CLASS_RELATIONSHIP_ID_ENCODINGS),
              column.fieldName,
              PARAMETER_ENCODING_FACTORY
            ),
            requiredRead
          )
        )
      )
      .build()
  }

  private fun relationshipIdEncodings(columns: List<ColumnElement>) = columns.mapNotNull { column ->
    column.relationshipBackedId?.let { relationship ->
      relationshipBackedIdEncoding(
        column = column,
        relationship = relationship
      )
    }
  }

  private fun relationshipBackedIdEncoding(column: ColumnElement, relationship: RelationshipElement): PropertySpec {
    val idType = relationship.nullableIdType
    return PropertySpec
      .builder(
        name = column.fieldName,
        type = ENCODING_TYPE.parameterizedBy(idType, idType, SQL_NULLABLE, SQL_NULLABLE)
      )
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
              relationship.referencedIdSerializedType.typeName.copy(nullable = true),
              relationship.deserializedDeclaredIdValue(CodeBlock.of("it"))
            ),
            namedArgument("encodingNullness", "%T", SQL_NULLABLE)
          )
        )
      )
      .build()
  }
}

private class FieldTarget(
  val fieldClass: ClassName,
  val ownerTypeArguments: List<TypeName>,
  val table: CodeBlock,
  val encodingsClassName: ClassName,
  val fieldName: String,
  val columnName: String,
  val position: Int? = null
)

private const val FULL_TEXT_ROW_ID_FIELD = "ROWID"
private const val FULL_TEXT_ROW_ID_COLUMN = "rowid"

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

private val ColumnElement.fieldClass
  get() = when {
    (isUnique || isId) && hasEntityKeyValue -> UNIQUE_COLUMN
    else -> COLUMN
  }

private val ColumnElement.relationshipBackedId
  get() = relationship?.takeIf { it.referencedIdRelationship != null }

private val RelationshipElement.nullableIdType get() = referencedIdType.typeName.copy(nullable = true)
