package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_GENERATED_VIEW
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.model.scalarFieldProperty
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.siimkinks.sqlitemagic.writer.ReadTableStructureWriter
import com.siimkinks.sqlitemagic.writer.entityProperties
import com.squareup.kotlinpoet.PropertySpec

internal class ViewTableWriter(
  private val environment: Environment
) : ViewWriter {
  override fun write(roundElement: ViewRoundElement) {
    readTableStructureWriter(roundElement.view)
      .write(
        codeGenerator = environment.codeGenerator,
        originatingFiles = roundElement.originatingFiles
      )
  }

  private fun readTableStructureWriter(view: ViewElement) = ReadTableStructureWriter(
    names = view.generationNames,
    modelClassName = view.modelClassName,
    isPublic = view.isPublic,
    constructorArguments = listOf(
      namedArgument("name", "%S", view.viewName),
      namedArgument("alias", "%N", VARIABLE_ALIAS),
      namedArgument("generatedView", "%T.%N", view.generationNames.daoClassName, FIELD_GENERATED_VIEW)
    ),
    columns = columnProperties(
      view = view,
      properties = view.properties,
      enclosingNullable = false
    ),
    readsRelated = view.hasBlocks,
    relationships = null,
    partialRead = ReadTableStructureWriter.PartialRead(
      entityProperties = entityProperties(
        modelName = view.modelName,
        properties = view.scalarProperties,
        partialRequirements = view.partialRequirements
      ),
      readsRelated = false
    ).takeIf { view.supportsPartialRead }
  )

  private fun columnProperties(
    view: ViewElement,
    properties: List<ViewPropertyElement>,
    enclosingNullable: Boolean
  ): List<PropertySpec> = properties.flatMap { property ->
    when (property) {
      is ViewScalarPropertyElement -> {
        val nullable = enclosingNullable || property.isNullable
        val decodesNull = property.transformer?.run { !isDefaultTransformer && decoderInputNullable } == true
        listOf(
          scalarFieldProperty(
            ownerType = view.modelClassName,
            fieldClass = COLUMN,
            fieldName = property.generatedFieldName,
            columnName = property.selectionKey,
            propertyName = property.access.path.displayName,
            deserializedType = property.deserializedType.typeName,
            sqlStorageType = property.sqlStorageType,
            transformer = property.transformer,
            isSqlNullable = nullable || decodesNull,
            isModelPathNullable = nullable,
            checksRequiredNull = true
          )
        )
      }
      is ViewEmbeddedPropertyElement -> columnProperties(
        view = view,
        properties = property.properties,
        enclosingNullable = enclosingNullable || property.isNullable
      )
      is ViewProjectionPropertyElement -> emptyList()
    }
  }
}
