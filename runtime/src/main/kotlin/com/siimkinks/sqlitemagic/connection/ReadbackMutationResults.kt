package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutationResults

internal class ReadbackMutationResults(private val connection: SQLiteConnection) : MutationResults {
  private var readback: SQLiteStatement? = null

  override fun executeInsert(statement: SQLiteStatement): Long {
    statement.step()
    return readResults { results ->
      when (results.getLong(0)) {
        0L -> -1L
        else -> results.getLong(1)
      }
    }
  }

  override fun executeUpdateDelete(statement: SQLiteStatement): Int {
    statement.step()
    return readResults { results -> results.getLong(0).toInt() }
  }

  private inline fun <T> readResults(read: (SQLiteStatement) -> T): T {
    val results = readback ?: connection
      .prepare("SELECT changes(), last_insert_rowid()")
      .also { readback = it }
    return try {
      results.step()
      read(results)
    } finally {
      results.reset()
    }
  }

  fun close() {
    readback?.close(quietly = true)
    readback = null
  }
}

internal class PinnedMutationResults(
  private val pooled: PooledConnection,
  private val results: MutationResults
) : MutationResults {
  override fun executeInsert(statement: SQLiteStatement) = pinned { results.executeInsert(statement) }

  override fun executeUpdateDelete(statement: SQLiteStatement) = pinned { results.executeUpdateDelete(statement) }

  private inline fun <T> pinned(block: () -> T): T {
    if (pooled.connection.inTransaction()) {
      return block()
    }
    pooled.execute("BEGIN IMMEDIATE")
    val result = try {
      block()
    } catch (failure: Throwable) {
      pooled.rollbackQuietly(failure)
      throw failure
    }
    pooled.commit()
    return result
  }
}
