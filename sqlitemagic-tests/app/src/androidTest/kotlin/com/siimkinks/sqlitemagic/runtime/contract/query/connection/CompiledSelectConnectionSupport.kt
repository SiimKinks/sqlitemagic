package com.siimkinks.sqlitemagic.runtime.contract.query.connection

import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.insertedRowId
import com.siimkinks.sqlitemagic.runtime.support.testApplication

internal const val ALTERNATE_DATABASE_NAME = "compiled-select-runtime-alternate.db"

internal fun insertCompiledSelectEntity(
  value: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = SimpleMutableEntity(
  id = null,
  value = value,
  boxedBoolean = true,
  primitiveBoolean = false
).let { entity ->
  entity.copy(
    id = insertedRowId(
      result = entity
        .insert()
        .usingConnection(connection)
        .execute(),
      modelName = "SimpleMutableEntity"
    )
  )
}

internal fun resetCompiledSelectDatabases() {
  testApplication()
    .deleteDatabase(ALTERNATE_DATABASE_NAME)
  SqliteMagic
    .getDefaultConnection()
    .clearData()
}
