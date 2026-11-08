@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.driver

import android.database.SQLException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver
import com.siimkinks.sqlitemagic.internal.CursorWindowReads
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.MutationResults
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.freshDatabasePath
import com.siimkinks.sqlitemagic.runtime.support.long
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import com.siimkinks.sqlitemagic.runtime.support.text
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

internal class FrameworkSQLiteDriverContractTest {
  private val databasePath = freshDatabasePath("framework-driver-contract.db")
  private val connection = FrameworkSQLiteDriver().open(databasePath)
  private val mutations = connection as MutationResults

  @After
  fun close() = connection.close()

  @Test
  fun valuesRoundTripWithTheirStorageTypes() {
    connection.execSQL(
      "CREATE TABLE value(id INTEGER PRIMARY KEY, number INTEGER, real REAL, text TEXT, blob BLOB, missing)"
    )
    val rowId = connection
      .prepare("INSERT INTO value(number, real, text, blob, missing) VALUES (?, ?, ?, ?, ?)")
      .use { statement ->
        statement.bindLong(
          index = 1,
          value = Long.MAX_VALUE
        )
        statement.bindDouble(
          index = 2,
          value = 1.5
        )
        statement.bindText(
          index = 3,
          value = "täxt"
        )
        statement.bindBlob(
          index = 4,
          value = byteArrayOf(1, 2, 3)
        )
        statement.bindNull(5)
        mutations.executeInsert(statement)
      }

    val row = connection
      .prepare("SELECT id, number, real, text, blob, missing FROM value")
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
          missingIsNull = statement.isNull(5),
          hasMoreRows = statement.step()
        )
      }

    assertThat(row).isEqualTo(
      StoredRow(
        columns = listOf("id", "number", "real", "text", "blob", "missing"),
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
        blob = listOf(1, 2, 3),
        missingIsNull = true,
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

    assertThat(values)
      .containsExactly("", 0, 0L, 0.0)
      .inOrder()
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
        statement.bindLong(
          index = 1,
          value = 7
        )
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
        statement.bindLong(
          index = 1,
          value = 1
        )
        val first = statement.readNullableLong()
        statement.reset()
        val sameBinding = statement.readNullableLong()
        statement.reset()
        statement.clearBindings()
        listOf(first, sameBinding, statement.readNullableLong())
      }

    assertThat(results)
      .containsExactly(1L, 1L, null)
      .inOrder()
  }

  @Test
  fun resultsLargerThanOneCursorWindowAreReadInOrder() {
    insertNotes()

    val ids = connection
      .prepare("SELECT id, body FROM note ORDER BY id")
      .use { statement ->
        buildList {
          while (statement.step()) {
            check(
              statement
                .getText(1)
                .startsWith("note ${statement.getLong(0) - 1} ")
            )
            add(statement.getLong(0))
          }
        }
      }

    assertThat(ids).isEqualTo((1L..5_000L).toList())
  }

  @Test
  fun rowCountIsTheNumberOfRowsOfTheCurrentExecution() {
    insertNotes()

    val counts = connection
      .prepare("SELECT id, body FROM note WHERE id <= ? ORDER BY id")
      .use { statement ->
        listOf(5_000L, 3L).map { last ->
          statement.reset()
          statement.bindLong(
            index = 1,
            value = last
          )
          statement.step()
          (statement as CursorWindowReads).rowCount
        }
      }

    assertThat(counts)
      .containsExactly(5_000, 3)
      .inOrder()
  }

  @Test
  fun rowsLargerThanTheCursorWindowFailInsteadOfReadingAsNull() {
    connection.execSQL("CREATE TABLE payload(id INTEGER PRIMARY KEY, data BLOB)")
    connection
      .prepare("INSERT INTO payload(id, data) VALUES (?, ?)")
      .use { statement ->
        listOf(1L to ByteArray(16), 2L to ByteArray(3 * 1024 * 1024)).forEach { (id, data) ->
          statement.bindLong(
            index = 1,
            value = id
          )
          statement.bindBlob(
            index = 2,
            value = data
          )
          statement.step()
          statement.reset()
        }
      }

    val readRows = mapOf(
      "the first row" to "SELECT data FROM payload WHERE id = 2",
      "a row after one that fits" to "SELECT data FROM payload ORDER BY id"
    ).mapValues { (_, sql) ->
      connection
        .prepare(sql)
        .use { statement ->
          runCatching {
            buildList {
              while (statement.step()) {
                add(statement.isNull(0))
              }
            }
          }
        }
        .exceptionOrNull()
        ?.let { it is SQLException }
    }

    assertThat(readRows).containsExactly("the first row", true, "a row after one that fits", true)
  }

  @Test
  fun queryOnlyConnectionsReadResultsLargerThanOneCursorWindowFromOneSnapshot() {
    insertNotesInWal()

    val read = withQueryOnlyReader { reader ->
      val ids = reader
        .prepare("SELECT id, body FROM note ORDER BY id")
        .use { statement ->
          buildList {
            while (statement.step()) {
              if (isEmpty()) {
                connection.execSQL("DELETE FROM note WHERE id > 1")
              }
              add(statement.getLong(0))
            }
          }
        }
      ids to reader.long("SELECT count(*) FROM note")
    }

    assertThat(read).isEqualTo((1L..5_000L).toList() to 1L)
  }

  @Test
  fun repeatedLargeReadsOnAQueryOnlyConnectionReadOneSnapshotUntilTheResultFitsOneWindow() {
    insertNotesInWal()

    val read = withQueryOnlyReader { reader ->
      reader
        .prepare("SELECT id, body FROM note ORDER BY id")
        .use { statement ->
          val reads = List(2) { execution ->
            statement.reset()
            buildList {
              while (statement.step()) {
                if (execution == 1 && isEmpty()) {
                  connection.execSQL("DELETE FROM note WHERE id > 1")
                }
                add(statement.getLong(0))
              }
            }
          }
          statement.reset()
          statement.step()
          connection.execSQL("INSERT INTO note(body) VALUES ('later')")
          reads to reader.long("SELECT count(*) FROM note")
        }
    }

    assertThat(read).isEqualTo(List(2) { (1L..5_000L).toList() } to 2L)
  }

  @Test
  fun largeReadsSharingAQueryOnlySnapshotKeepItUntilTheLastOneEnds() {
    insertNotesInWal()

    val read = withQueryOnlyReader { reader ->
      val shorter = reader.prepare("SELECT id, body FROM note WHERE id <= 4000 ORDER BY id")
      val longer = reader.prepare("SELECT id, body FROM note ORDER BY id")
      val ids = shorter.use {
        longer.use {
          shorter.step()
          longer.step()
          val shorterIds = shorter.idsFromCurrentRow()
          connection.execSQL("DELETE FROM note WHERE id > 1")
          shorterIds to longer.idsFromCurrentRow()
        }
      }
      Triple(
        first = ids.first,
        second = ids.second,
        third = reader.long("SELECT count(*) FROM note")
      )
    }

    assertThat(read).isEqualTo(
      Triple(
        first = (1L..4_000L).toList(),
        second = (1L..5_000L).toList(),
        third = 1L
      )
    )
  }

  @Test
  fun aQueryOnlySnapshotEndsWhenALargeReadIsResetEarly() {
    insertNotesInWal()

    val remaining = withQueryOnlyReader { reader ->
      reader
        .prepare("SELECT id, body FROM note ORDER BY id")
        .use { statement ->
          statement.step()
          statement.reset()
          connection.execSQL("DELETE FROM note WHERE id > 1")
          reader.long("SELECT count(*) FROM note")
        }
    }

    assertThat(remaining).isEqualTo(1L)
  }

  @Test
  fun writesDuringALargeReadOnAWritableConnectionCommitAtOnce() {
    insertNotesInWal()

    val remaining = FrameworkSQLiteDriver()
      .open(databasePath)
      .use { other ->
        connection
          .prepare("SELECT id, body FROM note ORDER BY id")
          .use { statement ->
            statement.step()
            connection.execSQL("DELETE FROM note WHERE id > 1")
            other.long("SELECT count(*) FROM note")
          }
      }

    assertThat(remaining).isEqualTo(1L)
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

    assertThat(failure)
      .hasMessageThat()
      .contains("UNIQUE constraint failed: tag.name")
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
        .use { it.step() to (it.getColumnCount() > 0) }

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

    assertThat(listOf(inTransaction, afterRollback))
      .containsExactly(true, false)
      .inOrder()
    assertThat(connection.long("SELECT group_concat(x) FROM item")).isEqualTo(2L)
  }

  @Test
  fun aTransactionBelongsToTheConnectionOnEveryThread() {
    connection.execSQL("CREATE TABLE item(x)")
    val worker = Executors.newSingleThreadExecutor()
    val inTransactionOnWorker = try {
      connection.execSQL("BEGIN IMMEDIATE")
      worker
        .submit<Boolean> {
          connection.execSQL("INSERT INTO item VALUES (1)")
          connection
            .inTransaction()
            .also { connection.execSQL("COMMIT") }
        }
        .get(5, SECONDS)
    } finally {
      worker.shutdownNow()
    }

    assertThat(listOf(inTransactionOnWorker, connection.inTransaction(), connection.long("SELECT count(*) FROM item")))
      .containsExactly(true, false, 1L)
      .inOrder()
  }

  @Test
  fun rollingBackToASavepointUndoesOnlyTheSavepoint() {
    connection.execSQL("CREATE TABLE item(x)")
    val cases = mapOf(
      "savepoint inside BEGIN" to listOf(
        "BEGIN IMMEDIATE",
        "INSERT INTO item VALUES (1)",
        "SAVEPOINT part",
        "INSERT INTO item VALUES (2)",
        "ROLLBACK TO part",
        "INSERT INTO item VALUES (3)",
        "RELEASE part",
        "COMMIT"
      ),
      "savepoint as the transaction" to listOf(
        "SAVEPOINT whole",
        "INSERT INTO item VALUES (1)",
        "SAVEPOINT part",
        "INSERT INTO item VALUES (2)",
        "ROLLBACK TRANSACTION TO SAVEPOINT part",
        "INSERT INTO item VALUES (3)",
        "RELEASE whole"
      )
    )

    val stored = cases.mapValues { (_, statements) ->
      connection.execSQL("DELETE FROM item")
      statements.forEach(connection::execSQL)
      connection.text("SELECT group_concat(x) FROM item")
    }

    assertThat(stored).containsExactly("savepoint inside BEGIN", "1,3", "savepoint as the transaction", "1,3")
  }

  @Test
  fun commitAndRollbackEndATransactionThatASavepointStarted() {
    connection.execSQL("CREATE TABLE item(x)")

    val stored = listOf("COMMIT", "ROLLBACK").associateWith { end ->
      connection.execSQL("DELETE FROM item")
      connection.execSQL("SAVEPOINT whole")
      connection.execSQL("INSERT INTO item VALUES (1)")
      connection.execSQL(end)
      connection.execSQL("BEGIN IMMEDIATE")
      connection.execSQL("INSERT INTO item VALUES (2)")
      connection.execSQL("COMMIT")
      connection.text("SELECT group_concat(x) FROM item")
    }

    assertThat(stored).containsExactly("COMMIT", "1,2", "ROLLBACK", "2")
    assertSqliteRejects(containing = "no transaction is active") { connection.execSQL("COMMIT") }
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
    assertThat(
      corrupt
        .readBytes()
        .toList()
    ).isEqualTo(garbage.toList())
  }

  @Test
  fun temporaryTablesAndPragmasStayOnTheConnectionOutsideTransactions() {
    connection.execSQL("CREATE TEMP TABLE scratch(x)")
    connection.execSQL("INSERT INTO scratch VALUES (1)")
    connection.execSQL("PRAGMA query_only = 1")

    val reads = List(3) { connection.long("SELECT count(*) FROM scratch") to connection.long("PRAGMA query_only") }

    assertThat(reads).containsExactly(1L to 1L, 1L to 1L, 1L to 1L)
  }

  @Test
  fun aWalDatabaseKeepsItsJournalModeWhenItOpensAgain() {
    val path = freshDatabasePath("framework-driver-reopened-wal.db")
    FrameworkSQLiteDriver()
      .open(path)
      .use { first ->
        first.text("PRAGMA journal_mode = WAL")
        first.execSQL("CREATE TABLE item(x)")
        first.execSQL("INSERT INTO item VALUES (1)")
      }

    val reopened = FrameworkSQLiteDriver()
      .open(path)
      .use { it.text("PRAGMA journal_mode") to it.long("SELECT count(*) FROM item") }

    assertThat(reopened).isEqualTo("wal" to 1L)
  }

  @Test
  fun inMemoryDatabasesOpen() {
    val journalMode = FrameworkSQLiteDriver()
      .open(":memory:")
      .use { it.text("PRAGMA journal_mode") }

    assertThat(journalMode).isEqualTo("memory")
  }

  private fun insertNotes() {
    connection.execSQL("CREATE TABLE note(id INTEGER PRIMARY KEY, body TEXT NOT NULL)")
    connection.execSQL("BEGIN IMMEDIATE")
    connection
      .prepare("INSERT INTO note(body) VALUES (?)")
      .use { statement ->
        repeat(5_000) { index ->
          statement.bindText(
            index = 1,
            value = "note $index ".padEnd(
              length = 1_000,
              padChar = 'x'
            )
          )
          mutations.executeInsert(statement)
        }
      }
    connection.execSQL("COMMIT")
  }

  private fun insertNotesInWal() {
    connection.text("PRAGMA journal_mode = WAL")
    insertNotes()
  }

  private fun <T> withQueryOnlyReader(block: (SQLiteConnection) -> T) = FrameworkSQLiteDriver()
    .open(databasePath)
    .use { reader ->
      reader.execSQL("PRAGMA query_only = 1")
      block(reader)
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

  private fun SQLiteStatement.idsFromCurrentRow() = buildList {
    do {
      add(getLong(0))
    } while (step())
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
    val missingIsNull: Boolean,
    val hasMoreRows: Boolean
  )
}
