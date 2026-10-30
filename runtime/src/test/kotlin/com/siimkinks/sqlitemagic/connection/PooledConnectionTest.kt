package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.MutationResults
import org.junit.Assert.assertThrows
import org.junit.Test

internal class PooledConnectionTest {
  private val connection = TrackingConnection(BundledSQLiteDriver().open(MEMORY_DATABASE))
  private val pooled = PooledConnection(connection)

  @Test
  fun `statements are prepared once per SQL and a nested use of the same SQL gets its own statement`() {
    repeat(3) { pooled.step("SELECT 1") }
    val nested = pooled.withStatement("SELECT 2") { outer ->
      outer.step()
      pooled.withStatement("SELECT 2") { inner ->
        inner.step()
        outer.getLong(0) + inner.getLong(0)
      }
    }

    assertThat(nested).isEqualTo(4L)
    assertThat(connection.prepared).containsExactly("SELECT 1", "SELECT 2", "SELECT 2").inOrder()
    assertThat(connection.statements.map(TrackingStatement::closed)).containsExactly(false, false, true).inOrder()
  }

  @Test
  fun `released statements are reset with their bindings cleared`() {
    val results = List(2) { use ->
      pooled.withStatement("SELECT ?") { statement ->
        if (use == 0) {
          statement.bindLong(1, 5)
        }
        statement.step()
        when {
          statement.isNull(0) -> null
          else -> statement.getLong(0)
        }
      }
    }

    assertThat(results).containsExactly(5L, null).inOrder()
  }

  @Test
  fun `the least recently used statement is closed beyond the cache size unless it is in use`() {
    val inUse = pooled.withStatement("SELECT 0") {
      for (index in 1..STATEMENT_CACHE_SIZE) {
        pooled.step("SELECT $index")
        if (index == 2) {
          pooled.step("SELECT 1")
        }
      }
      connection.statements.first().closed
    }

    assertThat(inUse).isFalse()
    assertThat(connection.statements.filter(TrackingStatement::closed)).containsExactly(connection.statements[2])
  }

  @Test
  fun `a statement prepared while every cached statement is in use stays open`() {
    fun nested(depth: Int): Long = pooled.withStatement("SELECT $depth") { statement ->
      when {
        depth < STATEMENT_CACHE_SIZE -> nested(depth + 1)
        else -> {
          statement.step()
          statement.getLong(0)
        }
      }
    }

    assertThat(nested(depth = 0)).isEqualTo(STATEMENT_CACHE_SIZE.toLong())
    assertThat(connection.statements.filter(TrackingStatement::closed)).isEmpty()
  }

  @Test
  fun `a failed statement reports its own failure and its SQL stays usable`() {
    connection.execSQL("CREATE TABLE tag(name TEXT NOT NULL UNIQUE)")
    connection.execSQL("INSERT INTO tag VALUES ('taken')")
    val insert = "INSERT INTO tag VALUES (?)"

    val failure = assertThrows(SQLiteException::class.java) {
      pooled.withStatement(insert) { statement ->
        statement.bindText(1, "taken")
        statement.step()
      }
    }
    pooled.withStatement(insert) { statement ->
      statement.bindText(1, "free")
      statement.step()
    }

    assertThat(failure).hasMessageThat().contains("UNIQUE constraint failed: tag.name")
    assertThat(connection.long("SELECT count(*) FROM tag")).isEqualTo(2L)
  }

  @Test
  fun `mutation results come from changes and last_insert_rowid`() {
    connection.execSQL("CREATE TABLE tag(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
    connection.execSQL("INSERT INTO tag(id, name) VALUES (7, 'first')")

    val results = listOf(
      "insert" to pooled.insert("INSERT INTO tag(name) VALUES ('second')"),
      "ignored insert" to pooled.insert("INSERT OR IGNORE INTO tag(name) VALUES ('second')"),
      "update" to pooled.updateDelete("UPDATE tag SET name = name || '!'"),
      "update without match" to pooled.updateDelete("UPDATE tag SET name = 'none' WHERE id = 99"),
      "delete" to pooled.updateDelete("DELETE FROM tag WHERE id = 7")
    )

    assertThat(results)
      .containsExactly(
        "insert" to 8L,
        "ignored insert" to -1L,
        "update" to 2L,
        "update without match" to 0L,
        "delete" to 1L
      )
      .inOrder()
  }

  @Test
  fun `mutations of a pooling driver run in their own transaction unless one is open`() {
    connection.execSQL("CREATE TABLE tag(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
    val pinned = PooledConnection(
      connection = connection,
      driverPoolsConnections = true
    )

    val alone = pinned.insert("INSERT INTO tag(name) VALUES ('first')")
    val duplicate = assertThrows(SQLiteException::class.java) {
      pinned.insert("INSERT INTO tag(name) VALUES ('first')")
    }
    val transactionLeftOpen = connection.inTransaction()
    connection.execSQL("BEGIN")
    val inOpenTransaction = pinned.insert("INSERT INTO tag(name) VALUES ('second')")
    connection.execSQL("ROLLBACK")

    assertThat(duplicate).hasMessageThat().contains("UNIQUE constraint failed: tag.name")
    assertThat(listOf(alone, inOpenTransaction)).containsExactly(1L, 2L).inOrder()
    assertThat(transactionLeftOpen).isFalse()
    assertThat(connection.long("SELECT count(*) FROM tag")).isEqualTo(1L)
  }

  @Test
  fun `a connection with native mutation results is used directly`() {
    val native = NativeMutationConnection(BundledSQLiteDriver().open(MEMORY_DATABASE))

    assertThat(PooledConnection(native).mutations).isSameInstanceAs(native)
  }

  private fun PooledConnection.step(sql: String) = withStatement(
    sql = sql,
    block = SQLiteStatement::step
  )

  private fun PooledConnection.insert(sql: String) = withStatement(
    sql = sql,
    block = mutations::executeInsert
  )

  private fun PooledConnection.updateDelete(sql: String) = withStatement(sql) { statement ->
    mutations
      .executeUpdateDelete(statement)
      .toLong()
  }

  private class NativeMutationConnection(
    delegate: SQLiteConnection
  ) : SQLiteConnection by delegate, MutationResults {
    override fun executeInsert(statement: SQLiteStatement) = 0L

    override fun executeUpdateDelete(statement: SQLiteStatement) = 0
  }
}
