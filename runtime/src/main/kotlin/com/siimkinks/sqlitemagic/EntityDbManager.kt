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
  internal fun insertStatement(insertSql: String) = insertStatementCache.get()
    ?: dbConnection
      .compileStatement(insertSql.format(""))
      .also(insertStatementCache::set)

  @CheckResult
  internal fun updateStatement(updateSql: String) = updateStatementCache.get()
    ?: dbConnection
      .compileStatement(updateSql.format(""))
      .also(updateStatementCache::set)

  @CheckResult
  internal fun compileStatement(sql: String) = dbConnection.compileStatement(sql)

  @CheckResult
  internal fun compileStatement(
    sql: String,
    @ConflictAlgorithm conflictAlgorithm: Int
  ) = dbConnection.compileStatement(
    sql.format(ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm])
  )

  private fun SupportSQLiteStatement.closeQuietly() = try {
    close()
  } catch (_: Exception) {
    // Each connection-owned statement gets an independent best-effort cleanup attempt.
  }
}
