package com.siimkinks.sqlitemagic.manager

internal fun findSchemaIdentityConflicts(
  structures: Iterable<Pair<String, DatabaseStructure>>
): List<SchemaIdentityConflict> {
  val ownersByNamespace = linkedMapOf<SchemaNamespace, MutableMap<String, SchemaIdentityOwner>>()
  return buildList {
    structures.forEach { (source, structure) ->
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.tables.keys,
        objectKind = SchemaObjectKind.TABLE,
        namespace = SchemaNamespace.PERSISTENT,
        ownersByNamespace = ownersByNamespace
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.indices.keys,
        objectKind = SchemaObjectKind.INDEX,
        namespace = SchemaNamespace.PERSISTENT,
        ownersByNamespace = ownersByNamespace
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryTables.keys,
        objectKind = SchemaObjectKind.TABLE,
        namespace = SchemaNamespace.TEMPORARY,
        ownersByNamespace = ownersByNamespace
      )
      collectSchemaIdentityConflicts(
        source = source,
        objects = structure.temporaryIndices.keys,
        objectKind = SchemaObjectKind.INDEX,
        namespace = SchemaNamespace.TEMPORARY,
        ownersByNamespace = ownersByNamespace
      )
    }
  }
}

private fun MutableList<SchemaIdentityConflict>.collectSchemaIdentityConflicts(
  source: String,
  objects: Set<String>,
  objectKind: SchemaObjectKind,
  namespace: SchemaNamespace,
  ownersByNamespace: MutableMap<SchemaNamespace, MutableMap<String, SchemaIdentityOwner>>
) {
  val owners = ownersByNamespace.getOrPut(namespace, defaultValue = ::linkedMapOf)
  objects.forEach { name ->
    val owner = SchemaIdentityOwner(
      source = source,
      objectKind = objectKind,
      name = name
    )
    val previousOwner = owners.putIfAbsent(name.normalizedSqlIdentifier(), owner)
    if (previousOwner != null) {
      add(
        SchemaIdentityConflict(
          namespace = namespace,
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
  val namespace: SchemaNamespace,
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

internal enum class SchemaNamespace(
  val displayName: String
) {
  PERSISTENT("main"),
  TEMPORARY("temp")
}

internal enum class SchemaObjectKind(
  val label: String
) {
  TABLE("table"),
  INDEX("index")
}
