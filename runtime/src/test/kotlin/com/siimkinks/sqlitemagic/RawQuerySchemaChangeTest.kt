package com.siimkinks.sqlitemagic

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import com.siimkinks.sqlitemagic.connection.databasePath
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class RawQuerySchemaChangeTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `reused raw queries read the columns of a schema changed after they were prepared`() {
    val alterColumn = "ALTER TABLE item ADD COLUMN note TEXT NOT NULL DEFAULT 'new'"
    val anotherConnectionFile = folder.databasePath()
    val changes = mapOf<String, Pair<String, (DbConnectionImpl) -> Unit>>(
      "same connection" to Pair(
        first = MEMORY_DATABASE,
        second = { connection -> connection.write { it.connection.execSQL(alterColumn) } }
      ),
      "another connection" to Pair(
        first = anotherConnectionFile,
        second = {
          BundledSQLiteDriver()
            .open(anotherConnectionFile)
            .use { it.execSQL(alterColumn) }
        }
      )
    )

    val results = changes.mapValues { (_, setup) ->
      val (fileName, change) = setup
      val connection = testDbConnection(
        database = TestGeneratedDatabase(
          tableCount = 1,
          schema = listOf(
            "CREATE TABLE item (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
            "INSERT INTO item VALUES (1, 'a')"
          )
        ),
        fileName = fileName
      )
      try {
        fun raw(sql: String) = SELECT
          .RAW(sql)
          .usingConnection(connection)
        val correct = raw("SELECT * FROM item")
        val wrong = raw("SELECT * FROM item WHERE id > 0")
        val correctEmpty = raw("SELECT * FROM item WHERE id < 0")
        val wrongEmpty = raw("SELECT * FROM item WHERE id < 1")
        val unknownColumns = raw("SELECT * FROM item ORDER BY id")
        fun rowsRead() = unknownColumns.useRows { rows ->
          val header = (0..<rows.columnCount).map(rows::columnName)
          rows.step()
          listOf(
            header,
            rows.read(
              index = rows.columnCount - 1,
              type = STRING
            )
          )
        }
        val beforeChange = listOf(correct, wrong, correctEmpty, wrongEmpty).map { query ->
          query
            .readAs(LONG, STRING)
            .execute()
        } + listOf(rowsRead())
        change(connection)
        val afterChange = listOf(
          correct
            .readAs(LONG, STRING, STRING)
            .execute(),
          assertThrows(IllegalStateException::class.java) {
            wrong
              .readAs(LONG, STRING)
              .takeFirst()
              .execute()
          }.message,
          correctEmpty
            .readAs(LONG, STRING, STRING)
            .execute(),
          assertThrows(IllegalStateException::class.java) {
            wrongEmpty
              .readAs(LONG, STRING)
              .useRows(Sequence<Row2<Long, String>>::toList)
          }.message,
          rowsRead()
        )
        beforeChange + afterChange
      } finally {
        connection.pool.close()
      }
    }

    val expected = listOf(
      listOf(Row2(1L, "a")),
      listOf(Row2(1L, "a")),
      emptyList<Any>(),
      emptyList<Any>(),
      listOf(listOf("id", "name"), "a"),
      listOf(Row3(1L, "a", "new")),
      "raw query returned 3 columns; readAs reads 2",
      emptyList<Any>(),
      "raw query returned 3 columns; readAs reads 2",
      listOf(listOf("id", "name", "note"), "new")
    )
    assertThat(results).isEqualTo(
      mapOf(
        "same connection" to expected,
        "another connection" to expected
      )
    )
  }
}
