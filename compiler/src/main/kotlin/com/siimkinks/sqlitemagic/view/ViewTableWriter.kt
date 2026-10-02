package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.BOOLEAN_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.CHAR_SEQUENCE
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NOT_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.UTILS
import com.siimkinks.sqlitemagic.model.parserName
import com.siimkinks.sqlitemagic.writer.ReadTableStructureWriter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NUMBER
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

internal class ViewTableWriter(
  private val environment: Environment
) : ViewWriter {
  override fun write(roundElement: ViewRoundElement) {
    val view = roundElement.view
    ReadTableStructureWriter
      .from(
        view = view,
        columns = columnProperties(
          view = view,
          properties = view.properties,
          enclosingNullable = false
        )
      )
      .write(
        codeGenerator = environment.codeGenerator,
        originatingFiles = roundElement.originatingFiles
      )
  }

  private fun columnProperties(
    view: ViewElement,
    properties: List<ViewPropertyElement>,
    enclosingNullable: Boolean
  ): List<PropertySpec> = properties.flatMap { property ->
    when (property) {
      is ViewScalarPropertyElement -> listOf(
        columnProperty(
          view = view,
          property = property,
          effectiveNullable = enclosingNullable || property.isNullable
        )
      )
      is ViewEmbeddedPropertyElement -> columnProperties(
        view = view,
        properties = property.properties,
        enclosingNullable = enclosingNullable || property.isNullable
      )
      is ViewProjectionPropertyElement -> emptyList()
    }
  }

  private fun columnProperty(
    view: ViewElement,
    property: ViewScalarPropertyElement,
    effectiveNullable: Boolean
  ) = PropertySpec
    .builder(
      name = property.generatedFieldName,
      type = columnType(
        view = view,
        property = property,
        effectiveNullable = effectiveNullable
      )
    )
    .initializer(
      columnInitializer(
        property = property,
        effectiveNullable = effectiveNullable
      )
    )
    .build()

  private fun columnType(
    view: ViewElement,
    property: ViewScalarPropertyElement,
    effectiveNullable: Boolean
  ): TypeName {
    val nullability = when {
      effectiveNullable -> NULLABLE
      else -> NOT_NULLABLE
    }
    val columnClass = columnClassName(property)
    return when {
      property.transformer != null -> columnClass.parameterizedBy(view.modelClassName, nullability)
      else -> {
        val valueType = property.deserializedType.typeName.copy(nullable = false)
        val returnType = valueType.copy(nullable = effectiveNullable)
        val storageType = checkNotNull(property.serializedType.sqlStorageType)
        val equivalentType = when {
          storageType.isNumeric -> NUMBER
          storageType == SqlStorageType.STRING -> CHAR_SEQUENCE
          else -> valueType
        }
        columnClass.parameterizedBy(
          valueType,
          returnType,
          equivalentType,
          view.modelClassName,
          nullability
        )
      }
    }
  }

  private fun columnInitializer(
    property: ViewScalarPropertyElement,
    effectiveNullable: Boolean
  ): CodeBlock {
    val parserName = checkNotNull(property.serializedType.sqlStorageType)
      .parserName(effectiveNullable)
    val physicalName = property.selectionKey.substringAfterLast('.')
    return CodeBlock.of(
      "%T(table = this, name = %S, valueParser = %T.%N, nullable = %L, alias = null)",
      columnClassName(property),
      physicalName,
      UTILS,
      parserName,
      effectiveNullable
    )
  }

  private fun columnClassName(property: ViewScalarPropertyElement) = when {
    property.transformer != null && !property.transformer.isDefaultTransformer -> property.transformer.generatedColumnClassName()
    property.transformer?.isDefaultTransformer == true -> BOOLEAN_COLUMN
    property.serializedType.sqlStorageType?.isNumeric == true -> NUMERIC_COLUMN
    else -> COLUMN
  }
}
