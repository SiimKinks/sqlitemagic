package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult

internal typealias IdentityColumn<M> = Column<*, *, *, M, NotNullable>

interface EntityAdapterMetadata {
  val moduleName: String?
  val tableName: String
  val insertSql: String
  val tablePosition: Int
  val withoutRowId: Boolean
  val maxColumns: Int
}

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

interface EntityRelationshipOperations {
  val ignoreNullValues: Boolean

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

interface EntityAdapter<M> : EntityAdapterMetadata {
  fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: M,
    generatedRelationshipIds: Map<String, Long>
  )
}

interface EntityIdentityAdapter<M> : EntityAdapter<M> {
  fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: M,
    byColumn: IdentityColumn<M>
  )

  fun bindNotNullForInsert(
    entity: M,
    values: SimpleArrayMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  )

  fun bindNotNullForUpdate(
    entity: M,
    values: SimpleArrayMap<String, Any>,
    byColumn: IdentityColumn<M>
  )

  fun identity(
    entity: M,
    byColumn: IdentityColumn<M>
  ): GeneratedEntityIdentity

  fun hasIdentityValue(
    entity: M,
    byColumn: IdentityColumn<M>
  ): Boolean

  fun updateStatementSql(byColumn: IdentityColumn<M>): String
}

interface EntityDefaultIdentityAdapter<M> : EntityIdentityAdapter<M> {
  val defaultIdentityColumn: IdentityColumn<M>
}

interface EntityGeneratedIdAdapter<M> {
  fun assignGeneratedId(
    entity: M,
    rowId: Long
  )
}
