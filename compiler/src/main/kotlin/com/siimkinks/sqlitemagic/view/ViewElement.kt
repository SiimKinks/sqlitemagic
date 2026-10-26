package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaProvider
import com.siimkinks.sqlitemagic.model.EmbeddedPropertyMetadata
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.siimkinks.sqlitemagic.model.alwaysRequired
import com.siimkinks.sqlitemagic.model.collectPartialRequirements
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.ClassName

internal sealed interface ViewPropertyElement : PropertyMetadata {
  fun flattenedLeaves(): List<ViewLeafElement> = when (this) {
    is ViewLeafElement -> listOf(this)
    is ViewEmbeddedPropertyElement -> properties.flatMap(ViewPropertyElement::flattenedLeaves)
  }
}

internal sealed interface ViewLeafElement : ViewPropertyElement {
  val selectionKey: String
}

internal data class ViewScalarPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  override val selectionKey: String,
  val generatedFieldName: String,
  val transformer: TransformerElement?
) : ViewLeafElement {
  val serializedType get() = transformer?.serializedType ?: deserializedType
  val sqlStorageType get() = checkNotNull(serializedType.sqlStorageType)
}

internal data class ViewProjectionPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  override val selectionKey: String,
  val target: GenerationNames
) : ViewLeafElement

internal data class ViewEmbeddedPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  override val construction: ModelConstruction,
  override val properties: List<ViewPropertyElement>
) : ViewPropertyElement, EmbeddedPropertyMetadata

internal data class ViewElement(
  val parsedType: ParsedType,
  val identity: SqliteSchemaIdentity,
  val artifactStem: String,
  val declarationOrder: Int,
  val moduleName: String?,
  val construction: ModelConstruction,
  val queryPropertyName: String,
  val properties: List<ViewPropertyElement>,
  val isPublic: Boolean
) : ParsedType by parsedType, SqliteSchemaProvider by identity {
  val modelClassName = checkNotNull(typeName as? ClassName) {
    "View type [$typeName] is not a class name"
  }
  val modelName = modelClassName.simpleName
  val viewName get() = rawName
  val generationNames = GenerationNames(artifactStem)
  val leaves = properties.flatMap(ViewPropertyElement::flattenedLeaves)
  val scalarProperties = leaves.filterIsInstance<ViewScalarPropertyElement>()

  val blocks = leaves.filterIsInstance<ViewProjectionPropertyElement>()
  val hasBlocks = blocks.isNotEmpty()

  /** A partial read (`mapTo(VIEW)`) cannot select a required block, so views with one have no partial reader. */
  val supportsPartialRead = blocks.all(ViewProjectionPropertyElement::isNullable)

  val partialRequirements by lazy {
    collectPartialRequirements(
      modelName = modelName,
      properties = properties,
      construction = construction,
      leaf = { it as? ViewScalarPropertyElement }
    )
  }
  val partialRequiredLeaves by lazy { partialRequirements.alwaysRequired() }
}
