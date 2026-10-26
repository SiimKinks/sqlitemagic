package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.db.SupportSQLiteStatement

@InternalSqliteMagicApi
interface EntityStatementBinder<M> {
  fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: M,
    generatedRelationshipIds: Map<String, Long>
  )
}

@InternalSqliteMagicApi
interface EntityIdentityStatementBinder<M> : EntityStatementBinder<M> {
  fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: M,
    columnName: String
  )

  fun bindNotNullForInsert(
    entity: M,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  )

  fun bindNotNullForUpdate(
    entity: M,
    values: MutableScatterMap<String, Any>,
    columnName: String
  )
}
