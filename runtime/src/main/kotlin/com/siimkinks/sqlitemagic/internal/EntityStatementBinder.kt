package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement

@InternalSqliteMagicApi
interface EntityStatementBinder<M> {
  fun bindToInsertStatement(
    statement: SQLiteStatement,
    entity: M,
    generatedRelationshipIds: Map<String, Long>
  )
}

@InternalSqliteMagicApi
interface EntityIdentityStatementBinder<M> : EntityStatementBinder<M> {
  fun bindToUpdateStatement(
    statement: SQLiteStatement,
    entity: M,
    columnName: String,
    generatedRelationshipIds: Map<String, Long>
  )

  fun bindNotNullForInsert(
    entity: M,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  )

  fun bindNotNullForUpdate(
    entity: M,
    values: MutableScatterMap<String, Any>,
    columnName: String,
    generatedRelationshipIds: Map<String, Long>
  )
}
