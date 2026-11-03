package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutationResults

internal class PooledConnection(
  val connection: SQLiteConnection,
  driverPoolsConnections: Boolean = false
) : AutoCloseable {
  private val readback = when (connection) {
    is MutationResults -> null
    else -> ReadbackMutationResults(connection)
  }
  val mutations: MutationResults = when {
    readback == null -> connection as MutationResults
    driverPoolsConnections -> PinnedMutationResults(
      pooled = this,
      results = readback
    )
    else -> readback
  }
  private val statements = LinkedHashMap<String, CachedStatement>(16, 0.75f, true)

  inline fun <T> withStatement(sql: String, block: (SQLiteStatement) -> T): T {
    val cached = acquireStatement(sql)
    val result = try {
      block(cached.statement)
    } catch (failure: Throwable) {
      releaseStatement(
        sql = sql,
        cached = cached,
        failed = true
      )
      throw failure
    }
    releaseStatement(
      sql = sql,
      cached = cached,
      failed = false
    )
    return result
  }

  fun acquireStatement(sql: String): CachedStatement {
    val cached = statements[sql]
    return when {
      cached == null -> CachedStatement(
        statement = connection.prepare(sql),
        transient = false
      ).also {
        statements[sql] = it
        evictLeastRecentlyUsed()
      }
      cached.inUse -> CachedStatement(
        statement = connection.prepare(sql),
        transient = true
      )
      else -> cached.also { it.inUse = true }
    }
  }

  fun releaseStatement(
    sql: String,
    cached: CachedStatement,
    failed: Boolean
  ) {
    val statement = cached.statement
    when {
      cached.transient -> statement.close(quietly = failed)
      else -> try {
        statement.reset()
        statement.clearBindings()
        cached.inUse = false
      } catch (resetFailure: Throwable) {
        statements.remove(sql)
        statement.close(quietly = true)
        if (!failed) {
          throw resetFailure
        }
      }
    }
  }

  fun execute(sql: String) = withStatement(
    sql = sql,
    block = SQLiteStatement::step
  )

  fun commit() {
    try {
      execute("COMMIT")
    } catch (failure: Throwable) {
      if (connection.inTransaction()) {
        rollbackQuietly(failure)
      }
      throw failure
    }
  }

  fun rollback() {
    if (!connection.inTransaction()) {
      return
    }
    try {
      execute("ROLLBACK")
    } catch (failure: Throwable) {
      if (connection.inTransaction()) {
        throw failure
      }
    }
  }

  fun rollbackQuietly(failure: Throwable) {
    try {
      rollback()
    } catch (rollbackFailure: Throwable) {
      failure.addSuppressed(rollbackFailure)
    }
  }

  private fun evictLeastRecentlyUsed() {
    if (statements.size <= STATEMENT_CACHE_SIZE) {
      return
    }
    val iterator = statements.values.iterator()
    while (iterator.hasNext()) {
      val cached = iterator.next()
      if (!cached.inUse) {
        iterator.remove()
        cached.statement.close(quietly = true)
        return
      }
    }
  }

  override fun close() {
    statements.values.forEach { it.statement.close(quietly = true) }
    statements.clear()
    readback?.close()
    connection.close()
  }
}

internal class CachedStatement(
  val statement: SQLiteStatement,
  val transient: Boolean
) {
  var inUse = true
}

internal fun AutoCloseable.close(quietly: Boolean) = when {
  quietly -> try {
    close()
  } catch (_: Exception) {
  }
  else -> close()
}

internal const val STATEMENT_CACHE_SIZE = 25
