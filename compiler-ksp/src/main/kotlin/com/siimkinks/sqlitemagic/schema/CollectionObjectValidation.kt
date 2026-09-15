package com.siimkinks.sqlitemagic.schema

import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.LINE_BREAK
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.NUL
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.RESERVED_PREFIX
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
  val artifactStem: String
)

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
    schemaIdentityRegistry.register(
      SchemaIdentityOwner(
        kind = "table",
        rawName = table.tableName,
        identity = table.identity,
        typeKey = table.typeKey
      )
    )
    artifactStemRegistry.register(
      ArtifactStemOwner(
        typeKey = table.typeKey,
        qualifiedName = table.qualifiedName,
        artifactStem = table.artifactStem
      )
    )
  }

  fun unregisterTable(table: TableElement) {
    schemaIdentityRegistry.unregister(
      SchemaIdentityOwner(
        kind = "table",
        rawName = table.tableName,
        identity = table.identity,
        typeKey = table.typeKey
      )
    )
    artifactStemRegistry.unregister(
      ArtifactStemOwner(
        typeKey = table.typeKey,
        qualifiedName = table.qualifiedName,
        artifactStem = table.artifactStem
      )
    )
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
  artifactStem: String,
  qualifiedNames: List<String>,
  includeRenameSuggestion: Boolean
): String {
  val declarations = qualifiedNames.joinToString(
    separator = "', '",
    prefix = "'",
    postfix = "'"
  )
  val suffix = when {
    includeRenameSuggestion -> ". Rename one model or an enclosing class to make the generated names unique."
    else -> ""
  }
  return "Cannot generate code for declarations $declarations: their declaration paths map to the same generated name '$artifactStem'$suffix"
}
