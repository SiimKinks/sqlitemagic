package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.SQL_TYPE
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.siimkinks.sqlitemagic.writer.parameterEncodingPolicy
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

internal fun scalarFieldProperty(
  ownerType: ClassName,
  fieldClass: ClassName,
  fieldName: String,
  columnName: String,
  propertyName: String,
  deserializedType: TypeName,
  sqlStorageType: SqlStorageType,
  transformer: TransformerElement?,
  isSqlNullable: Boolean,
  isModelPathNullable: Boolean,
  checksRequiredNull: Boolean
): PropertySpec {
  val read = ScalarFieldRead(
    deserializedType = deserializedType,
    sqlStorageType = sqlStorageType,
    transformer = transformer,
    isSqlNullable = isSqlNullable,
    checksRequiredNull = checksRequiredNull,
    columnName = columnName,
    propertyName = propertyName
  )
  val encoding = parameterEncodingPolicy(
    inputNullable = isModelPathNullable,
    encoderInputNullable = read.customTransformer?.encoderInputNullable == true,
    encodingNullable = read.customTransformer?.serializedTypeCanBeNull == true
  )
  return PropertySpec
    .builder(
      name = fieldName,
      type = fieldClass.parameterizedBy(
        ownerType,
        read.descriptorResultType,
        read.nullness,
        read.selectedType,
        deserializedType.copy(nullable = isModelPathNullable),
        encoding.nullness,
        encoding.parameterResultType(read.descriptorResultType)
      )
    )
    .initializer(
      callCode(
        callee = CodeBlock.of("%T", fieldClass),
        arguments = listOf(
          namedArgument("table", "this"),
          namedArgument("name", "%S", columnName),
          namedArgument("nullness", "%T", read.nullness),
          namedArgument("parameterEncoding", "%T.%N.%M()", SQL_TYPE, read.descriptorName, encoding.factory),
          namedArgument("selectedRead", "{ cursor, index -> %L }", read.selectedRead)
        )
      )
    )
    .build()
}
