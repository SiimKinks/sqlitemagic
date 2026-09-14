package com.siimkinks.sqlitemagic.schema

import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.LINE_BREAK
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.NUL
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.RESERVED_PREFIX

internal data class SchemaIdentityOwner(
  val kind: String,
  val rawName: String,
  val identity: SqliteSchemaIdentity,
  val typeKey: TypeKey? = null
) {
  val normalizedKey get() = identity.normalizedKey
}

internal class SchemaIdentityRegistry(
  owners: Iterable<SchemaIdentityOwner> = emptyList()
) {
  private val ownersByKey = linkedMapOf<SqliteSchemaKey, SchemaIdentityOwner>()

  init {
    owners.forEach(::register)
  }

  fun lookup(key: SqliteSchemaKey) = ownersByKey[key]

  fun register(owner: SchemaIdentityOwner) = ownersByKey
    .putIfAbsent(owner.normalizedKey, owner)
}

internal data class ArtifactStemOwner(
  val typeKey: TypeKey,
  val qualifiedName: String,
  val artifactStem: String
)

internal class ArtifactStemRegistry(
  owners: Iterable<ArtifactStemOwner> = emptyList()
) {
  private val ownersByStem = linkedMapOf<String, ArtifactStemOwner>()

  init {
    owners.forEach(::register)
  }

  fun lookup(artifactStem: String) = ownersByStem[artifactStem]

  fun register(owner: ArtifactStemOwner) = ownersByStem
    .putIfAbsent(owner.artifactStem, owner)
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
