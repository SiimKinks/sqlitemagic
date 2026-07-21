package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType

data class PropertyPath(
  val segments: List<String>
) {
  init {
    require(segments.isNotEmpty()) {
      "A property path must contain at least one segment"
    }
  }

  val propertyName get() = segments.last()

  fun child(propertyName: String) = PropertyPath(segments + propertyName)
}

data class PropertyAccess(
  val path: PropertyPath,
  val isMutable: Boolean
)

interface PropertyMetadata {
  val access: PropertyAccess
  val deserializedType: ParsedType
  val isNullable: Boolean
}

sealed interface PropertyElement : PropertyMetadata

data class ColumnPropertyElement(
  val column: ColumnElement
) : PropertyElement, PropertyMetadata by column

data class EmbeddedPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  val prefix: String,
  val cumulativePrefix: String,
  val construction: ModelConstruction,
  val properties: List<PropertyElement>
) : PropertyElement
