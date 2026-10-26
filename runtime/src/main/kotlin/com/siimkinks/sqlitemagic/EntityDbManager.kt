package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteStatement
import java.util.concurrent.atomic.AtomicReference

internal class EntityDbManager(initialConnection: DbConnectionImpl) {
  private val insertStatementCache = AtomicReference<SupportSQLiteStatement?>()
  private val updateStatementCache = AtomicReference<SupportSQLiteStatement?>()
  private var connection: DbConnectionImpl? = initialConnection

  private val dbConnection get() = checkNotNull(connection) { "DB connection closed" }

  fun close() {
    insertStatementCache
      .getAndSet(null)
      ?.closeQuietly()
    updateStatementCache
      .getAndSet(null)
      ?.closeQuietly()
    connection = null
  }

  @CheckResult
  fun insertStatement(insertSql: String) = insertStatementCache.cached(insertSql)

  @CheckResult
  fun updateStatement(updateSql: String) = updateStatementCache.cached(updateSql)

  private fun AtomicReference<SupportSQLiteStatement?>.cached(sql: String) = get() ?: synchronized(this) {
    get() ?: dbConnection
      .compileStatement(sql.format(""))
      .also(::set)
  }

  @CheckResult
  fun compileStatement(sql: String) = dbConnection.compileStatement(sql)

  @CheckResult
  fun compileStatement(
    sql: String,
    @ConflictAlgorithm conflictAlgorithm: Int
  ) = dbConnection.compileStatement(
    sql.format(ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm])
  )
}

internal fun SupportSQLiteStatement.closeQuietly() = try {
  close()
} catch (_: Exception) {
}
