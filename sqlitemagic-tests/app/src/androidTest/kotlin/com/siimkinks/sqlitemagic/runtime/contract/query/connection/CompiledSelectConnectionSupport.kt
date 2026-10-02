package com.siimkinks.sqlitemagic.runtime.contract.query.connection

import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.testApplication

internal const val ALTERNATE_DATABASE_NAME = "compiled-select-runtime-alternate.db"

internal fun insertCompiledSelectEntity(
  value: String,
  connection: DbConnection? = null
): SimpleMutableEntity {
  val entity = SimpleMutableEntity(
    id = null,
    value = value,
    boxedBoolean = true,
    primitiveBoolean = false
  )
  val result = when (connection) {
    null -> entity
      .insert()
      .execute()
    else -> entity
      .insert()
      .usingConnection(connection)
      .execute()
  }
  return when (result) {
    is EntityInsertResult.Inserted -> entity.copy(id = checkNotNull(result.rowId))
    EntityInsertResult.Ignored -> error("Deterministic insert was ignored")
  }
}

internal fun resetCompiledSelectDatabases() {
  testApplication()
    .deleteDatabase(ALTERNATE_DATABASE_NAME)
  SqliteMagic
    .getDefaultConnection()
    .clearData()
}
