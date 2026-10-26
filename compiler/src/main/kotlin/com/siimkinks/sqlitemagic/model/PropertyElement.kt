package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR

internal data class PropertyPath(
  val segments: List<String>
) {
  init {
    require(segments.isNotEmpty()) {
      "A property path must contain at least one segment"
    }
  }

  val propertyName get() = segments.last()
  val displayName get() = segments.joinToString(separator = ".")

  fun child(propertyName: String) = PropertyPath(segments + propertyName)
}

internal data class PropertyAccess(
  val path: PropertyPath,
  val isMutable: Boolean
)

internal interface PropertyMetadata {
  val access: PropertyAccess
  val deserializedType: ParsedType
  val isNullable: Boolean
}

internal interface EmbeddedPropertyMetadata : PropertyMetadata {
  val construction: ModelConstruction
  val properties: List<PropertyMetadata>
}

/**
 * Maps each [leaf] that a partial read must select to the path of the embedded value it is required within, or to
 * null. A leaf is required when it is non-null and set through a constructor parameter, since generated readers
 * cannot fall back to constructor defaults. Inside a nullable or mutable embedded value it is required only when that
 * value is read.
 */
internal fun <L : Any> collectPartialRequirements(
  modelName: String,
  properties: List<PropertyMetadata>,
  construction: ModelConstruction,
  leaf: (PropertyMetadata) -> L?
): Map<L, String?> = buildMap {
  fun visit(properties: List<PropertyMetadata>, construction: ModelConstruction, within: String?) {
    properties.forEach { property ->
      val required = !property.isNullable &&
          construction.strategy == PRIMARY_CONSTRUCTOR &&
          construction.constructorParameterIndex(property.access.path) != null
      when (property) {
        is EmbeddedPropertyMetadata -> visit(
          properties = property.properties,
          construction = property.construction,
          within = when {
            required -> within
            else -> "$modelName.${property.access.path.displayName}"
          }
        )
        else -> if (required) leaf(property)?.let { this[it] = within }
      }
    }
  }
  visit(
    properties = properties,
    construction = construction,
    within = null
  )
}

internal fun <L> Map<L, String?>.alwaysRequired() = filterValues { it == null }.keys

internal sealed interface PropertyElement : PropertyMetadata {
  fun flattenedColumns(): List<ColumnElement> = when (this) {
    is ColumnPropertyElement -> listOf(column)
    is EmbeddedPropertyElement -> properties.flatMap(PropertyElement::flattenedColumns)
  }
}

internal data class ColumnPropertyElement(
  val column: ColumnElement
) : PropertyElement, PropertyMetadata by column

internal data class EmbeddedPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  override val construction: ModelConstruction,
  override val properties: List<PropertyElement>
) : PropertyElement, EmbeddedPropertyMetadata
