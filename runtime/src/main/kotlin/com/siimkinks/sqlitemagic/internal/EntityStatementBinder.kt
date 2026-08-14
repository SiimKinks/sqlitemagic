package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.db.SupportSQLiteStatement

interface EntityStatementBinder<M> {
  fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: M,
    generatedRelationshipIds: Map<String, Long>
  )
}

interface EntityIdentityStatementBinder<M> : EntityStatementBinder<M> {
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
}
