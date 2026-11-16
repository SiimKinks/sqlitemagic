package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.manager.SchemaObjectKind.FULL_TEXT_INDEX
import com.siimkinks.sqlitemagic.manager.SchemaObjectKind.FULL_TEXT_SHADOW_TABLE
import com.siimkinks.sqlitemagic.manager.SchemaObjectKind.INDEX
import com.siimkinks.sqlitemagic.manager.SchemaObjectKind.TABLE
import com.siimkinks.sqlitemagic.manager.SchemaObjectKind.VIEW
import com.siimkinks.sqlitemagic.schema.KeyedCollisionRegistry

internal fun findSchemaIdentityConflicts(
  structures: Iterable<Pair<String, DatabaseStructure>>
): List<SchemaIdentityConflict> {
  val ownersByKey = KeyedCollisionRegistry<SqliteSchemaKey, SchemaIdentityOwner>()
  return buildList {
    structures.forEach { (source, structure) ->
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.tables.keys,
        objectKind = TABLE,
        schema = MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.indices.keys,
        objectKind = INDEX,
        schema = MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.views.keys,
        objectKind = VIEW,
        schema = MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.fullTextIndices.keys,
        objectKind = FULL_TEXT_INDEX,
        schema = MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.fullTextIndices.values
          .flatMap(FullTextIndexStructure::shadowTables)
          .toSet(),
        objectKind = FULL_TEXT_SHADOW_TABLE,
        schema = MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryTables.keys,
        objectKind = TABLE,
        schema = TEMPORARY,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryIndices.keys,
        objectKind = INDEX,
        schema = TEMPORARY,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryViews.keys,
        objectKind = VIEW,
        schema = TEMPORARY,
        ownersByKey = ownersByKey
      )
    }
  }
}

private fun MutableList<SchemaIdentityConflict>.collectSchemaIdentityConflicts(
  source: String,
  objects: Set<String>,
  objectKind: SchemaObjectKind,
  schema: SqliteSchema,
  ownersByKey: KeyedCollisionRegistry<SqliteSchemaKey, SchemaIdentityOwner>
) {
  objects.forEach { name ->
    val identity = SqliteSchemaIdentity(
      schema = schema,
      identifier = SqliteIdentifier(name)
    )
    val owner = SchemaIdentityOwner(
      source = source,
      objectKind = objectKind,
      name = name
    )
    val previousOwner = ownersByKey.register(
      key = identity.normalizedKey,
      value = owner
    )
    if (previousOwner != null) {
      add(
        SchemaIdentityConflict(
          schema = schema,
          source = source,
          objectKind = objectKind,
          name = name,
          previousOwner = previousOwner
        )
      )
    }
  }
}

internal data class SchemaIdentityConflict(
  val schema: SqliteSchema,
  val source: String,
  val objectKind: SchemaObjectKind,
  val name: String,
  val previousOwner: SchemaIdentityOwner
)

internal data class SchemaIdentityOwner(
  val source: String,
  val objectKind: SchemaObjectKind,
  val name: String
)

internal enum class SchemaObjectKind(
  val label: String
) {
  TABLE("table"),
  INDEX("index"),
  VIEW("view"),
  FULL_TEXT_INDEX("full-text index"),
  FULL_TEXT_SHADOW_TABLE("full-text shadow table")
}
