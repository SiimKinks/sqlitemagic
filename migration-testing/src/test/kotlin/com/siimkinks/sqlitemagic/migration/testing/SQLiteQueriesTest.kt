package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SQLiteQueriesTest {
  @Test
  fun `parameters bind without SQL interpolation and results retain SQLite value types`() {
    BundledSQLiteDriver()
      .open(":memory:")
      .use { connection ->
        SQLiteQueries.execute(
          connection = connection,
          sql = "CREATE TABLE t(i INTEGER, r REAL, s TEXT, b BLOB, n TEXT)"
        )
        SQLiteQueries.execute(
          connection = connection,
          sql = "INSERT INTO t VALUES (?, ?, ?, ?, ?)",
          bindArgs = listOf(true, 1.25f, "O'Brien", byteArrayOf(1, 2), null)
        )
        val result = SQLiteQueries.query(
          connection = connection,
          sql = "SELECT i, r, s, hex(b), n FROM t WHERE s = ?",
          bindArgs = listOf("O'Brien")
        )
        assertThat(result)
          .isEqualTo(
            QueryResult(
              columns = listOf("i", "r", "s", "hex(b)", "n"),
              rows = listOf(listOf(1L, 1.25, "O'Brien", "0102", null))
            )
          )
      }
  }

  @Test(expected = IllegalStateException::class)
  fun `unsupported parameter types fail explicitly`() {
    BundledSQLiteDriver()
      .open(":memory:")
      .use { connection ->
        SQLiteQueries.query(
          connection = connection,
          sql = "SELECT ?",
          bindArgs = listOf(Any())
        )
      }
  }
}
