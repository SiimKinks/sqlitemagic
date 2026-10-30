@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.driver

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.MutationResults
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

internal class FrameworkSQLiteDriverContractTest {
  private val connection = FrameworkSQLiteDriver().open(freshDatabasePath("framework-driver-contract.db"))
  private val mutations = connection as MutationResults

  @After
  fun close() = connection.close()

  @Test
  fun valuesRoundTripWithTheirStorageTypes() {
    connection.execSQL(
      "CREATE TABLE value(id INTEGER PRIMARY KEY, number INTEGER, real REAL, text TEXT, blob BLOB, nothing)"
    )
    val rowId = connection
      .prepare("INSERT INTO value(number, real, text, blob, nothing) VALUES (?, ?, ?, ?, ?)")
      .use { statement ->
        statement.bindLong(1, Long.MAX_VALUE)
        statement.bindDouble(2, 1.5)
        statement.bindText(3, "täxt")
        statement.bindBlob(4, byteArrayOf(1, 2, 3))
        statement.bindNull(5)
        mutations.executeInsert(statement)
      }

    val row = connection
      .prepare("SELECT id, number, real, text, blob, nothing FROM value")
      .use { statement ->
        val columns = List(
          size = statement.getColumnCount(),
          init = statement::getColumnName
        )
        statement.step()
        StoredRow(
          columns = columns,
          types = List(
            size = statement.getColumnCount(),
            init = statement::getColumnType
          ),
          id = statement.getLong(0),
          number = statement.getLong(1),
          real = statement.getDouble(2),
          text = statement.getText(3),
          blob = statement.getBlob(4).toList(),
          nothingIsNull = statement.isNull(5),
          hasMoreRows = statement.step()
        )
      }

    assertThat(row).isEqualTo(
      StoredRow(
        columns = listOf("id", "number", "real", "text", "blob", "nothing"),
        types = listOf(
          SQLITE_DATA_INTEGER,
          SQLITE_DATA_INTEGER,
          SQLITE_DATA_FLOAT,
          SQLITE_DATA_TEXT,
          SQLITE_DATA_BLOB,
          SQLITE_DATA_NULL
        ),
        id = rowId,
        number = Long.MAX_VALUE,
        real = 1.5,
        text = "täxt",
        blob = listOf<Byte>(1, 2, 3),
        nothingIsNull = true,
        hasMoreRows = false
      )
    )
  }

  @Test
  fun sqlNullReadsAsTheBundledDriverReadsIt() {
    val values = connection
      .prepare("SELECT NULL")
      .use { statement ->
        statement.step()
        listOf(
          statement.getText(0),
          statement.getBlob(0).size,
          statement.getLong(0),
          statement.getDouble(0)
        )
      }

    assertThat(values).containsExactly("", 0, 0L, 0.0).inOrder()
  }

  @Test
  fun columnsAreKnownBeforeTheStatementRunsWithItsBindings() {
    val result = connection
      .prepare("SELECT ? AS value")
      .use { statement ->
        val columns = List(
          size = statement.getColumnCount(),
          init = statement::getColumnName
        )
        statement.bindLong(1, 7)
        statement.step()
        columns to statement.getLong(0)
      }

    assertThat(result).isEqualTo(listOf("value") to 7L)
  }

  @Test
  fun resetReexecutesWithBindingsUntilTheyAreCleared() {
    val results = connection
      .prepare("SELECT ?")
      .use { statement ->
        statement.bindLong(1, 1)
        val first = statement.readNullableLong()
        statement.reset()
        val sameBinding = statement.readNullableLong()
        statement.reset()
        statement.clearBindings()
        listOf(first, sameBinding, statement.readNullableLong())
      }

    assertThat(results).containsExactly(1L, 1L, null).inOrder()
  }

  @Test
  fun resultsLargerThanOneCursorWindowAreReadInOrder() {
    connection.execSQL("CREATE TABLE note(id INTEGER PRIMARY KEY, body TEXT NOT NULL)")
    connection.execSQL("BEGIN IMMEDIATE")
    connection
      .prepare("INSERT INTO note(body) VALUES (?)")
      .use { statement ->
        repeat(5_000) { index ->
          statement.bindText(1, "note $index ".padEnd(1_000, 'x'))
          mutations.executeInsert(statement)
        }
      }
    connection.execSQL("COMMIT")

    val ids = connection
      .prepare("SELECT id, body FROM note ORDER BY id")
      .use { statement ->
        buildList {
          while (statement.step()) {
            check(statement.getText(1).startsWith("note ${statement.getLong(0) - 1} "))
            add(statement.getLong(0))
          }
        }
      }

    assertThat(ids).isEqualTo((1L..5_000L).toList())
  }

  @Test
  fun mutationsReportRowIdsAndChangeCounts() {
    connection.execSQL("CREATE TABLE tag(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
    connection.execSQL("INSERT INTO tag(id, name) VALUES (7, 'first')")

    val results = listOf(
      "insert" to insert("INSERT INTO tag(name) VALUES ('second')"),
      "ignored insert" to insert("INSERT OR IGNORE INTO tag(name) VALUES ('second')"),
      "update" to updateDelete("UPDATE tag SET name = name || '!'"),
      "update without match" to updateDelete("UPDATE tag SET name = 'none' WHERE id = 99"),
      "common table expression delete" to updateDelete(
        "WITH doomed AS (SELECT id FROM tag WHERE id = 7) DELETE FROM tag WHERE id IN doomed"
      )
    )

    assertThat(results)
      .containsExactly(
        "insert" to 8L,
        "ignored insert" to -1L,
        "update" to 2L,
        "update without match" to 0L,
        "common table expression delete" to 1L
      )
      .inOrder()
  }

  @Test
  fun constraintViolationsKeepTheFrameworkExceptionType() {
    connection.execSQL("CREATE TABLE tag(name TEXT NOT NULL UNIQUE)")
    connection.execSQL("INSERT INTO tag VALUES ('taken')")

    val failure = assertThrows(SQLiteConstraintException::class.java) {
      insert("INSERT INTO tag VALUES ('taken')")
    }

    assertThat(failure).hasMessageThat().contains("UNIQUE constraint failed: tag.name")
  }

  @Test
  fun statementsReturnRowsWhenTheyHaveResultColumns() {
    connection.execSQL("CREATE TABLE item(x)")
    val cases = listOf(
      "  -- comment\n/* block */ select 1" to true,
      "WITH one AS (SELECT 1) SELECT * FROM one" to true,
      "VALUES (1)" to true,
      "PRAGMA user_version" to true,
      "EXPLAIN SELECT 1" to true,
      "INSERT INTO item VALUES (1)" to false,
      "PRAGMA user_version = 3" to false,
      "WITH doomed AS (SELECT x FROM item) DELETE FROM item WHERE x IN doomed" to false,
      "CREATE INDEX item_x ON item(x)" to false
    )

    for ((sql, returnsRows) in cases) {
      val observed = connection
        .prepare(sql)
        .use { statement -> statement.step() to (statement.getColumnCount() > 0) }

      assertWithMessage(sql)
        .that(observed)
        .isEqualTo(returnsRows to returnsRows)
    }
  }

  @Test
  fun returningClausesReturnRowsWhereTheEngineSupportsThem() {
    connection.execSQL("CREATE TABLE item(x)")
    connection.execSQL("INSERT INTO item VALUES (1), (2)")

    fun deleted() = connection
      .prepare("DELETE FROM item RETURNING x")
      .use { statement ->
        buildList {
          while (statement.step()) {
            add(statement.getLong(0))
          }
        }
      }

    when {
      supportsSqlite(
        version = connection.text("SELECT sqlite_version()"),
        minor = 35
      ) -> assertThat(deleted()).containsExactly(1L, 2L)
      else -> assertSqliteRejects(containing = "syntax error") { deleted() }
    }
  }

  @Test
  fun transactionStatementsControlTheConnectionTransaction() {
    connection.execSQL("CREATE TABLE item(x)")

    connection.execSQL("BEGIN IMMEDIATE")
    val inTransaction = connection.inTransaction()
    connection.execSQL("INSERT INTO item VALUES (1)")
    connection.execSQL("ROLLBACK")
    val afterRollback = connection.inTransaction()
    connection.execSQL("BEGIN IMMEDIATE")
    connection.execSQL("INSERT INTO item VALUES (2)")
    connection.execSQL("COMMIT")

    assertThat(listOf(inTransaction, afterRollback)).containsExactly(true, false).inOrder()
    assertThat(connection.long("SELECT group_concat(x) FROM item")).isEqualTo(2L)
  }

  @Test
  fun corruptionIsReportedAndTheDatabaseFilesAreKept() {
    val corrupt = File(freshDatabasePath("framework-driver-corrupt.db"))
    val garbage = ByteArray(4096) { 0x5a }
    corrupt.writeBytes(garbage)

    assertThrows(SQLiteDatabaseCorruptException::class.java) {
      FrameworkSQLiteDriver()
        .open(corrupt.path)
        .use { it.long("SELECT count(*) FROM sqlite_master") }
    }
    assertThat(corrupt.readBytes().toList()).isEqualTo(garbage.toList())
  }

  @Test
  fun inMemoryDatabasesOpen() {
    val memory = FrameworkSQLiteDriver().open(":memory:")
    val journalMode = memory.use { it.text("PRAGMA journal_mode") }

    assertThat(journalMode).isEqualTo("memory")
  }

  private fun insert(sql: String) = connection
    .prepare(sql)
    .use(mutations::executeInsert)

  private fun updateDelete(sql: String) = connection
    .prepare(sql)
    .use { statement ->
      mutations
        .executeUpdateDelete(statement)
        .toLong()
    }

  private fun SQLiteStatement.readNullableLong(): Long? {
    step()
    return when {
      isNull(0) -> null
      else -> getLong(0)
    }
  }

  private data class StoredRow(
    val columns: List<String>,
    val types: List<Int>,
    val id: Long,
    val number: Long,
    val real: Double,
    val text: String,
    val blob: List<Byte>,
    val nothingIsNull: Boolean,
    val hasMoreRows: Boolean
  )
}
