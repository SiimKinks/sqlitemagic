package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.transformer.TransformerElement

internal sealed interface PropertySeed {
  val roundElement: PropertyRoundElement
}

internal data class ColumnSeed(
  override val roundElement: PropertyRoundElement,
  val diagnosticPath: String,
  val access: PropertyAccess,
  val columnName: String,
  val isSchemaNullable: Boolean,
  val explicitDefaultValue: String?,
  val transformer: TransformerElement?,
  val relationshipTypeKey: TypeKey?,
  val isHandledRecursively: Boolean,
  val onDeleteCascade: Boolean,
  val idAnnotation: KSAnnotation?,
  val isUnique: Boolean,
  val index: IndexElement?,
  val belongsToIndex: String?,
  val embeddedPrefixes: List<String>
) : PropertySeed

internal data class EmbeddedSeed(
  override val roundElement: PropertyRoundElement,
  val access: PropertyAccess,
  val prefix: String,
  val cumulativePrefix: String,
  val construction: ModelConstruction,
  val properties: List<PropertySeed>
) : PropertySeed

internal data class TableSeed(
  val classDeclaration: KSClassDeclaration,
  val parsedType: ParsedType,
  val tableName: String,
  val artifactStem: String,
  val declarationOrder: Int,
  val options: Set<TableOption>,
  val construction: ModelConstruction,
  val propertySeeds: List<PropertySeed>
) : ParsedType by parsedType {
  val idSeed = propertySeeds
    .filterIsInstance<ColumnSeed>()
    .singleOrNull { it.idAnnotation != null }
}
