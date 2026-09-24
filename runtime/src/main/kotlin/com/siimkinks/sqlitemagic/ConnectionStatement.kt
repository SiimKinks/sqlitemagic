package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement

internal class ConnectionStatement(
  private val sql: String,
  private val args: Array<String?>?,
  initialConnection: DbConnectionImpl?
) {
  private val lock = Any()
  private var statement = initialConnection?.let(::compile)

  fun <T> execute(
    dbConnection: DbConnectionImpl,
    operation: (SupportSQLiteStatement) -> T
  ): T = synchronized(lock) {
    operation(
      statement ?: compile(dbConnection).also { statement = it }
    )
  }

  private fun compile(dbConnection: DbConnectionImpl) = dbConnection
    .compileStatement(sql)
    .also { statement ->
      SqlUtil.bindAllArgsAsStrings(statement, args)
    }
}
