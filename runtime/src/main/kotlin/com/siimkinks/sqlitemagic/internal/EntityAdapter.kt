package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.UniqueColumn
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult

internal fun EntityAdapterMetadata.identityColumnName(column: Unique<*>) = when (column) {
  is UniqueColumn<*, *, *, *, *, *, *> -> {
    require(column.table.name == tableName) { "Column does not identify an entity property" }
    column.name
  }
}

internal fun <M> EntityAdapter<M>.recursiveOrNull() = this as? EntityRecursiveAdapter<M>

@InternalSqliteMagicApi
interface EntityAdapterMetadata {
  val moduleName: String?
  val tableName: String
  val insertSql: String
  val tablePosition: Int
  val withoutRowId: Boolean
  val maxColumns: Int
}

@InternalSqliteMagicApi
interface EntityRecursiveAdapter<M> : EntityAdapter<M> {
  val triggerTableNames: Array<String>

  fun insertRelationships(
    entity: M,
    operations: EntityRelationshipOperations
  ): Boolean

  fun updateRelationships(
    entity: M,
    operations: EntityRelationshipOperations
  ): Boolean

  fun persistRelationships(
    entity: M,
    operations: EntityRelationshipOperations
  ): Boolean
}

@InternalSqliteMagicApi
interface EntityRelationshipOperations {
  fun <M> insert(
    adapter: EntityDefaultIdentityAdapter<M>,
    entity: M
  ): EntityInsertResult

  fun <M> update(
    adapter: EntityDefaultIdentityAdapter<M>,
    entity: M
  ): Boolean

  fun <M> persist(
    adapter: EntityDefaultIdentityAdapter<M>,
    entity: M
  ): EntityPersistResult

  fun rememberGeneratedId(
    columnName: String,
    rowId: Long
  )
}

@InternalSqliteMagicApi
interface EntityAdapter<M> : EntityAdapterMetadata, EntityStatementBinder<M>

@InternalSqliteMagicApi
interface EntityIdentityAdapter<M> : EntityAdapter<M>, EntityIdentityStatementBinder<M> {
  /** The stored value of the identity column [columnName], as the statement binders bind it. */
  fun identity(
    entity: M,
    columnName: String
  ): Any

  fun hasIdentityValue(
    entity: M,
    columnName: String
  ): Boolean

  fun updateStatementSql(columnName: String): String
}

@InternalSqliteMagicApi
interface EntityDefaultIdentityAdapter<M> : EntityIdentityAdapter<M> {
  val defaultIdentityColumnName: String
}

@InternalSqliteMagicApi
interface EntityGeneratedIdAdapter<M> : EntityAdapter<M> {
  fun assignGeneratedId(
    entity: M,
    rowId: Long
  )
}
