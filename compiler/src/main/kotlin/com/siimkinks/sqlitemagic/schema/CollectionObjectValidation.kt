package com.siimkinks.sqlitemagic.schema

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.Origin.JAVA_LIB
import com.google.devtools.ksp.symbol.Origin.KOTLIN_LIB
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.fulltext.FullTextIndexElement
import com.siimkinks.sqlitemagic.fulltext.fullTextIndexArtifactStem
import com.siimkinks.sqlitemagic.fulltext.fullTextSchemaOwners
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.internal.SqliteIdentifierProvider
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.LINE_BREAK
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.NUL
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.RESERVED_PREFIX
import com.siimkinks.sqlitemagic.tableStructureClassName
import com.siimkinks.sqlitemagic.view.ViewElement

internal data class SchemaIdentityOwner(
  val kind: String,
  val rawName: String,
  val identity: SqliteSchemaIdentity,
  val typeKey: TypeKey? = null
) {
  val normalizedKey get() = identity.normalizedKey
}

internal class SchemaIdentityRegistry {
  private val ownersByKey = KeyedCollisionRegistry<SqliteSchemaKey, SchemaIdentityOwner>()

  fun lookup(key: SqliteSchemaKey) = ownersByKey.lookup(key)

  fun register(owner: SchemaIdentityOwner) = ownersByKey.register(
    key = owner.normalizedKey,
    value = owner
  )

  fun unregister(owner: SchemaIdentityOwner) = ownersByKey.unregister(
    key = owner.normalizedKey,
    value = owner
  )
}

internal data class ArtifactStemOwner(
  val typeKey: TypeKey,
  val qualifiedName: String,
  val artifactStem: String,
  val fullTextIndexName: String? = null
) {
  val displayName
    get() = when (fullTextIndexName) {
      null -> "'$qualifiedName'"
      else -> "full-text index '$fullTextIndexName' of '$qualifiedName'"
    }
  val tableClassName get() = tableStructureClassName(artifactStem).canonicalName
}

internal class ArtifactStemRegistry {
  private val ownersByStem = KeyedCollisionRegistry<String, ArtifactStemOwner>()

  fun lookup(artifactStem: String) = ownersByStem.lookup(artifactStem)

  fun register(owner: ArtifactStemOwner) = ownersByStem.register(
    key = owner.artifactStem,
    value = owner
  )

  fun unregister(owner: ArtifactStemOwner) = ownersByStem.unregister(
    key = owner.artifactStem,
    value = owner
  )
}

internal class CollectionObjectValidationRegistry {
  val schemaIdentityRegistry = SchemaIdentityRegistry()
  val artifactStemRegistry = ArtifactStemRegistry()

  fun registerTable(table: TableElement) {
    table.schemaIdentityOwners().forEach(schemaIdentityRegistry::register)
    table.artifactStemOwners().forEach(artifactStemRegistry::register)
  }

  fun unregisterTable(table: TableElement) {
    table.schemaIdentityOwners().forEach(schemaIdentityRegistry::unregister)
    table.artifactStemOwners().forEach(artifactStemRegistry::unregister)
  }

  fun replaceTable(
    previous: TableElement?,
    replacement: TableElement
  ) {
    previous?.let(::unregisterTable)
    registerTable(replacement)
  }

  fun registerView(view: ViewElement) {
    schemaIdentityRegistry.register(
      SchemaIdentityOwner(
        kind = "view",
        rawName = view.viewName,
        identity = view.identity,
        typeKey = view.typeKey
      )
    )
    artifactStemRegistry.register(
      ArtifactStemOwner(
        typeKey = view.typeKey,
        qualifiedName = view.qualifiedName,
        artifactStem = view.artifactStem
      )
    )
  }

  fun unregisterView(view: ViewElement) {
    schemaIdentityRegistry.unregister(
      SchemaIdentityOwner(
        kind = "view",
        rawName = view.viewName,
        identity = view.identity,
        typeKey = view.typeKey
      )
    )
    artifactStemRegistry.unregister(
      ArtifactStemOwner(
        typeKey = view.typeKey,
        qualifiedName = view.qualifiedName,
        artifactStem = view.artifactStem
      )
    )
  }

  fun replaceView(
    previous: ViewElement?,
    replacement: ViewElement
  ) {
    previous?.let(::unregisterView)
    registerView(replacement)
  }

  fun registerIndex(index: IndexElement) {
    schemaIdentityRegistry.register(
      SchemaIdentityOwner(
        kind = "index",
        rawName = index.name,
        identity = index.identity
      )
    )
  }
}

private fun TableElement.schemaIdentityOwners() = listOf(
  SchemaIdentityOwner(
    kind = "table",
    rawName = tableName,
    identity = identity,
    typeKey = typeKey
  )
) + fullTextIndexes.flatMap { index ->
  fullTextSchemaOwners(
    name = index.name,
    columnSize = index.columnSize,
    typeKey = typeKey
  )
}

private fun TableElement.artifactStemOwners() = tableArtifactStemOwners(
  typeKey = typeKey,
  qualifiedName = qualifiedName,
  artifactStem = artifactStem,
  fullTextIndexNames = fullTextIndexes.map(FullTextIndexElement::name)
)

internal fun tableArtifactStemOwners(
  typeKey: TypeKey,
  qualifiedName: String,
  artifactStem: String,
  fullTextIndexNames: List<String>
) = listOf(
  ArtifactStemOwner(
    typeKey = typeKey,
    qualifiedName = qualifiedName,
    artifactStem = artifactStem
  )
) + fullTextIndexNames.distinct().map { name ->
  ArtifactStemOwner(
    typeKey = typeKey,
    qualifiedName = qualifiedName,
    artifactStem = fullTextIndexArtifactStem(name),
    fullTextIndexName = name
  )
}

internal enum class SqliteIdentifierProblem {
  LINE_BREAK,
  NUL,
  RESERVED_PREFIX
}

internal fun SqliteIdentifierProvider.sqliteIdentifierProblem() = when {
  rawName.contains('\n') || rawName.contains('\r') -> LINE_BREAK
  rawName.contains('\u0000') -> NUL
  normalizedName.startsWith(prefix = "sqlite_") -> RESERVED_PREFIX
  else -> null
}

internal fun artifactStemCollisionMessage(
  owners: List<ArtifactStemOwner>,
  includeRenameSuggestion: Boolean
): String {
  val artifactStem = owners.first().artifactStem
  val names = owners.joinToString(transform = ArtifactStemOwner::displayName)
  return when {
    owners.any { it.fullTextIndexName != null } -> "Cannot generate code for $names: they map to the same " +
        "generated name '$artifactStem'. Rename a model, an enclosing class or a full-text index to make the " +
        "generated names unique."
    else -> {
      val suffix = when {
        includeRenameSuggestion -> ". Rename one model or an enclosing class to make the generated names unique."
        else -> ""
      }
      "Cannot generate code for declarations $names: their declaration paths map to the same generated name " +
          "'$artifactStem'$suffix"
    }
  }
}

internal fun libraryClassCollisionMessage(owner: ArtifactStemOwner): String {
  val rename = when (owner.fullTextIndexName) {
    null -> "the declaration or an enclosing class"
    else -> "the full-text index"
  }
  return "Cannot generate code for ${owner.displayName}: its generated class '${owner.tableClassName}' is already " +
      "declared on the classpath. Rename $rename."
}

internal fun Resolver.declaresLibraryClass(owner: ArtifactStemOwner) = getClassDeclarationByName(
  getKSNameFromString(owner.tableClassName)
)
  ?.origin
  .let { it == KOTLIN_LIB || it == JAVA_LIB }
