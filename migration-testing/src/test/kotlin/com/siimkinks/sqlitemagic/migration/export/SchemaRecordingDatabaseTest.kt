package com.siimkinks.sqlitemagic.migration.export

import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.junit.Assert.assertThrows
import org.junit.Test

internal class SchemaRecordingDatabaseTest {
  @Test
  fun `records persistent creation, configuration and transactions`() {
    val recorder = SchemaRecordingDatabase().apply {
      execSQL("PRAGMA foreign_keys = ON")
      execSQL("BEGIN IMMEDIATE")
      execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY)")
      execSQL("CREATE VIEW titles AS SELECT id FROM books")
      execSQL("CREATE UNIQUE INDEX books_id ON books(id)")
      execSQL("COMMIT")
    }
    assertThat(
      recorder.snapshot(
        databaseId = "main",
        releaseVariant = "release",
        version = 3,
        modules = listOf("Feature", "main")
      )
    ).isEqualTo(
      ReleaseSchemaSnapshot(
        formatVersion = 1,
        databaseId = "main",
        releaseVariant = "release",
        version = 3,
        modules = listOf("Feature", "main"),
        configuration = MigrationConfiguration(foreignKeysEnabled = true),
        tableSql = listOf("CREATE TABLE books (id INTEGER PRIMARY KEY)"),
        views = listOf(
          MigrationViewSchema(
            name = "titles",
            createSql = "CREATE VIEW titles AS SELECT id FROM books"
          )
        ),
        indexSql = listOf("CREATE UNIQUE INDEX books_id ON books(id)")
      )
    )
    assertThat(recorder.transactionEvents)
      .containsExactly("begin", "commit")
      .inOrder()
  }

  @Test
  fun `absent foreign key pragma records disabled configuration`() {
    assertThat(
      SchemaRecordingDatabase().snapshot(
        databaseId = "main",
        releaseVariant = "release",
        version = 1,
        modules = listOf("main")
      )
    ).isEqualTo(
      ReleaseSchemaSnapshot(
        formatVersion = 1,
        databaseId = "main",
        releaseVariant = "release",
        version = 1,
        modules = listOf("main"),
        configuration = MigrationConfiguration(foreignKeysEnabled = false),
        tableSql = emptyList(),
        views = emptyList(),
        indexSql = emptyList()
      )
    )
  }

  @Test
  fun `unsupported calls and temporary SQL fail`() {
    val cases: Map<String, (SchemaRecordingDatabase) -> Unit> = mapOf(
      "schema outside transaction" to { it.execSQL("CREATE TABLE books (id INTEGER)") },
      "query" to {
        it.execSQL("BEGIN IMMEDIATE")
        it.execSQL("SELECT 1")
      },
      "bound argument" to {
        it.execSQL("BEGIN IMMEDIATE")
        it
          .prepare("CREATE TABLE books (id INTEGER DEFAULT ?)")
          .use { statement -> statement.bindLong(index = 1, value = 1L) }
      },
      "nested transaction" to {
        it.execSQL("BEGIN IMMEDIATE")
        it.execSQL("BEGIN IMMEDIATE")
      },
      "foreign keys within transaction" to {
        it.execSQL("BEGIN IMMEDIATE")
        it.execSQL("PRAGMA foreign_keys = ON")
      },
      "temporary object" to {
        it.execSQL("BEGIN IMMEDIATE")
        it.execSQL("CREATE TEMPORARY TABLE scratch (id INTEGER)")
      }
    )
    cases.forEach { (label, action) ->
      assertThrows(label, RuntimeException::class.java) { action(SchemaRecordingDatabase()) }
    }
  }

  @Test
  fun `unsuccessful or unfinished transaction cannot produce evidence`() {
    mapOf(
      "unfinished" to false,
      "rolled back" to true
    ).forEach { (label, rolledBack) ->
      val recorder = SchemaRecordingDatabase()
      recorder.execSQL("BEGIN IMMEDIATE")
      if (rolledBack) recorder.execSQL("ROLLBACK")
      assertThrows(label, IllegalStateException::class.java) {
        recorder.snapshot(
          databaseId = "main",
          releaseVariant = "release",
          version = 1,
          modules = listOf("main")
        )
      }
    }
  }

  @Test
  fun `generated quoted names and multiline definitions are retained verbatim`() {
    val sql = "CREATE VIEW IF NOT EXISTS \"book\"\"titles\narchive\" AS SELECT 'first\nsecond' AS title"
    val recorder = SchemaRecordingDatabase().apply {
      execSQL("BEGIN IMMEDIATE")
      execSQL(sql)
      execSQL("COMMIT")
    }
    assertThat(recorder.views).containsExactly(
      MigrationViewSchema(
        name = "book\"titles\narchive",
        createSql = sql
      )
    )
  }

  @Test
  fun `malformed generated view headers are rejected`() {
    val cases = listOf(
      """CREATE VIEW "unterminated AS SELECT 1""",
      """CREATE VIEW "title" SELECT 1""",
      """CREATE VIEW "title" AS """
    )
    cases.forEach { sql ->
      val recorder = SchemaRecordingDatabase()
      recorder.execSQL("BEGIN IMMEDIATE")
      assertThrows(sql, IllegalArgumentException::class.java) { recorder.execSQL(sql) }
    }
  }
}
