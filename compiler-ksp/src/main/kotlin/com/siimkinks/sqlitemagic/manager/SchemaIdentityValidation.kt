package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.schema.KeyedCollisionRegistry
import com.siimkinks.sqlitemagic.schema.SqliteIdentifier
import com.siimkinks.sqlitemagic.schema.SqliteSchema
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.SqliteSchemaKey

internal fun findSchemaIdentityConflicts(
  structures: Iterable<Pair<String, DatabaseStructure>>
): List<SchemaIdentityConflict> {
  val ownersByKey = KeyedCollisionRegistry<SqliteSchemaKey, SchemaIdentityOwner>()
  return buildList {
    structures.forEach { (source, structure) ->
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.tables.keys,
        objectKind = SchemaObjectKind.TABLE,
        schema = SqliteSchema.MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.indices.keys,
        objectKind = SchemaObjectKind.INDEX,
        schema = SqliteSchema.MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.views.keys,
        objectKind = SchemaObjectKind.VIEW,
        schema = SqliteSchema.MAIN,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryTables.keys,
        objectKind = SchemaObjectKind.TABLE,
        schema = SqliteSchema.TEMPORARY,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryIndices.keys,
        objectKind = SchemaObjectKind.INDEX,
        schema = SqliteSchema.TEMPORARY,
        ownersByKey = ownersByKey
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryViews.keys,
        objectKind = SchemaObjectKind.VIEW,
        schema = SqliteSchema.TEMPORARY,
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
      identifier = SqliteIdentifier.from(name)
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
  VIEW("view")
}
