package com.siimkinks.sqlitemagic.migration.export

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.junit.Assert.assertThrows
import org.junit.Test

class SchemaRecordingDatabaseTest {
  @Test
  fun recordsPersistentCreationConfigurationAndTransactions() {
    val recorder = SchemaRecordingDatabase()
    recorder.database.apply {
      setForeignKeyConstraintsEnabled(true)
      beginTransaction()
      execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY)")
      execSQL("CREATE VIEW titles AS SELECT id FROM books")
      execSQL("CREATE UNIQUE INDEX books_id ON books(id)")
      setTransactionSuccessful()
      endTransaction()
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
    assertThat(recorder.transactionEvents).containsExactly("begin", "successful", "end")
      .inOrder()
  }

  @Test
  fun absentForeignKeyCallRecordsDisabledConfiguration() {
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
  fun unsupportedCallsAndTemporarySqlFail() {
    val cases: Map<String, (SchemaRecordingDatabase) -> Unit> = mapOf(
      "schema outside transaction" to { it.database.execSQL("CREATE TABLE books (id INTEGER)") },
      "query" to { it.database.query("SELECT 1") },
      "nested transaction" to {
        it.database.beginTransaction()
        it.database.beginTransaction()
      },
      "foreign keys within transaction" to {
        it.database.beginTransaction()
        it.database.setForeignKeyConstraintsEnabled(true)
      },
      "temporary object" to {
        it.database.beginTransaction()
        it.database.execSQL("CREATE TEMPORARY TABLE scratch (id INTEGER)")
      }
    )
    cases.forEach { (label, action) ->
      assertThrows(label, RuntimeException::class.java) { action(SchemaRecordingDatabase()) }
    }
  }

  @Test
  fun unsuccessfulOrUnfinishedTransactionCannotProduceEvidence() {
    listOf(false, true).forEach { finish ->
      val recorder = SchemaRecordingDatabase()
      recorder.database.beginTransaction()
      if (finish) recorder.database.endTransaction()
      assertThrows(IllegalStateException::class.java) {
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
  fun generatedQuotedNamesAndMultilineDefinitionsAreRetainedVerbatim() {
    val sql = "CREATE VIEW IF NOT EXISTS \"book\"\"titles\narchive\" AS SELECT 'first\nsecond' AS title"
    val recorder = SchemaRecordingDatabase()
    recorder.database.apply {
      beginTransaction()
      execSQL(sql)
      setTransactionSuccessful()
      endTransaction()
    }
    assertThat(recorder.views).containsExactly(
      MigrationViewSchema(
        name = "book\"titles\narchive",
        createSql = sql
      )
    )
  }

  @Test
  fun malformedGeneratedViewHeadersAreRejected() {
    val cases = listOf(
      "CREATE VIEW \"unterminated AS SELECT 1",
      "CREATE VIEW \"title\" SELECT 1",
      "CREATE VIEW \"title\" AS "
    )
    cases.forEach { sql ->
      val recorder = SchemaRecordingDatabase()
      recorder.database.beginTransaction()
      assertThrows(sql, IllegalArgumentException::class.java) { recorder.database.execSQL(sql) }
    }
  }
}
