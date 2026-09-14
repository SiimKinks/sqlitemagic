package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.SqliteSchemaProvider

internal sealed interface ViewPropertySeed {
  val roundElement: ViewPropertyRoundElement

  fun sourceProperties(): Sequence<Pair<PropertyPath, KSPropertyDeclaration>>
}

internal data class ViewLeafSeed(
  override val roundElement: ViewPropertyRoundElement,
  val access: PropertyAccess,
  val selectionKey: String
) : ViewPropertySeed {
  override fun sourceProperties() = sequenceOf(roundElement.sourceDeclaration)
    .filterIsInstance<KSPropertyDeclaration>()
    .map { declaration -> access.path to declaration }
}

internal data class ViewEmbeddedSeed(
  override val roundElement: ViewPropertyRoundElement,
  val access: PropertyAccess,
  val prefix: String,
  val cumulativePrefix: String,
  val construction: ModelConstruction,
  val properties: List<ViewPropertySeed>
) : ViewPropertySeed {
  override fun sourceProperties() = properties
    .asSequence()
    .flatMap(ViewPropertySeed::sourceProperties)
}

internal data class ViewSeed(
  val declaration: KSClassDeclaration,
  val parsedType: ParsedType,
  val identity: SqliteSchemaIdentity,
  val artifactStem: String,
  val declarationOrder: Int,
  val construction: ModelConstruction,
  val query: ViewQueryRoundElement,
  val properties: List<ViewPropertySeed>,
  val isPublic: Boolean
) : ParsedType by parsedType, SqliteSchemaProvider by identity {
  fun sourceProperties(): Map<PropertyPath, KSPropertyDeclaration> = properties
    .asSequence()
    .flatMap(ViewPropertySeed::sourceProperties)
    .toMap()
}
