package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyMetadata
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.SqliteSchemaProvider
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

data class ViewGenerationNames(
  val packageName: String,
  val artifactStem: String
) {
  val daoClassName = ClassName(packageName, "SqliteMagic_${artifactStem}_Dao")
  val tableClassName = ClassName(PACKAGE_ROOT, "${artifactStem}Table")
}

data class ViewQueryElement(
  val ownerType: ClassName,
  val propertyName: String,
  val declaredType: TypeName
)

enum class ViewProjectionKind {
  TABLE,
  VIEW
}

sealed interface ViewPropertyElement : PropertyMetadata {
  val expandedWidth: Int

  fun flattenedLeaves(): List<ViewLeafElement>
}

sealed interface ViewLeafElement : ViewPropertyElement {
  val selectionKey: String
}

data class ViewScalarPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  override val selectionKey: String,
  val generatedFieldName: String,
  val transformer: TransformerElement?
) : ViewLeafElement {
  override val expandedWidth = 1
  val serializedType get() = transformer?.serializedType ?: deserializedType

  override fun flattenedLeaves() = listOf(this)
}

data class ViewProjectionPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  override val selectionKey: String,
  val targetKind: ViewProjectionKind,
  val targetArtifactStem: String,
  val targetConstruction: ModelConstruction,
  override val expandedWidth: Int
) : ViewLeafElement {
  init {
    require(expandedWidth > 0) {
      "A view projection must have a positive expanded width"
    }
  }

  override fun flattenedLeaves() = listOf(this)
}

data class ViewEmbeddedPropertyElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  val prefix: String,
  val cumulativePrefix: String,
  val construction: ModelConstruction,
  val properties: List<ViewPropertyElement>
) : ViewPropertyElement {
  override val expandedWidth get() = properties.sumOf(ViewPropertyElement::expandedWidth)

  override fun flattenedLeaves() = properties.flatMap(ViewPropertyElement::flattenedLeaves)
}

data class ViewElement(
  val parsedType: ParsedType,
  val identity: SqliteSchemaIdentity,
  val artifactStem: String,
  val declarationOrder: Int,
  val moduleName: String?,
  val construction: ModelConstruction,
  val query: ViewQueryElement,
  val properties: List<ViewPropertyElement>,
  val isPublic: Boolean = true
) : ParsedType by parsedType, SqliteSchemaProvider by identity {
  val modelClassName = checkNotNull(typeName as? ClassName) {
    "View type [$typeName] is not a class name"
  }
  val modelName = modelClassName.simpleName
  val packageName = modelClassName.packageName
  val viewName get() = rawName
  val generationNames = ViewGenerationNames(
    packageName = packageName,
    artifactStem = artifactStem
  )
  val expandedWidth get() = properties.sumOf(ViewPropertyElement::expandedWidth)
  val leaves get() = properties.flatMap(ViewPropertyElement::flattenedLeaves)
  val scalarProperties get() = leaves.filterIsInstance<ViewScalarPropertyElement>()
}
