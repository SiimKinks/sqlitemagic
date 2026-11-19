package com.siimkinks.sqlitemagic.runtime.contract.manager

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.LetterFtsTable.Companion.LETTER_FTS
import com.siimkinks.sqlitemagic.LetterTable.Companion.LETTER
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.fixture.model.fulltext.Letter
import com.siimkinks.sqlitemagic.fixture.model.fulltext.LetterAuthor
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openDatabaseFile
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.userVersion
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import com.siimkinks.sqlitemagic.runtime.support.withWriter
import org.junit.Test

private const val DOWNGRADE_DATABASE_NAME = "schema-downgrade-runtime-test.db"
private const val FULL_TEXT_DOWNGRADE_DATABASE_NAME = "full-text-schema-downgrade-runtime-test.db"

internal class SchemaDowngradeRuntimeTest {
  @Test
  fun defaultDowngradeReplacesApplicationTablesAndRecreatesUsableGeneratedSchema() = withNamedDatabase(
    databaseName = DOWNGRADE_DATABASE_NAME
  ) { application ->
    val database = SqliteMagicDatabase()
    val version = database.dbVersion
    val metadata = openDatabaseFile(
      application = application,
      databaseName = DOWNGRADE_DATABASE_NAME
    ).use { oldConnection ->
      oldConnection.execSQL("CREATE TABLE library_books (book_key TEXT PRIMARY KEY, title_text TEXT)")
      oldConnection.execSQL("INSERT INTO library_books VALUES ('old-book', 'discarded-title')")
      oldConnection.execSQL("CREATE TABLE obsolete_application_table (id INTEGER PRIMARY KEY AUTOINCREMENT)")
      oldConnection.execSQL("INSERT INTO obsolete_application_table DEFAULT VALUES")
      oldConnection.execSQL("PRAGMA user_version = ${version + 1}")
      oldConnection.execSQL("CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)")
      oldConnection.execSQL(
        "INSERT INTO android_metadata SELECT 'en_US' WHERE NOT EXISTS (SELECT 1 FROM android_metadata)"
      )
      oldConnection.readStrings("SELECT locale FROM android_metadata ORDER BY locale")
    }

    openNamedConnection(
      application = application,
      databaseName = DOWNGRADE_DATABASE_NAME,
      database = database
    ).use { connection ->
      assertThat(connection.withWriter(SQLiteConnection::userVersion)).isEqualTo(version)
      val tables = schemaObjectNames(
        master = SQLITE_MASTER,
        type = "table",
        connection = connection
      )
      assertThat(tables).containsAtLeast("library_books", "android_metadata", "sqlite_sequence")
      assertThat(tables).doesNotContain("obsolete_application_table")
      assertThat(
        connection.withWriter { it.readStrings("SELECT locale FROM android_metadata ORDER BY locale") }
      ).containsExactlyElementsIn(metadata)
      assertRowsIgnoringOrder(
        table = LIBRARY_BOOK,
        expected = emptyList(),
        connection = connection
      )

      val book = LibraryBook(
        id = "after-downgrade",
        title = "recreated-schema-title"
      )
      insertedResult(
        result = book
          .insert()
          .usingConnection(connection)
          .execute(),
        modelName = LibraryBook::class.java.simpleName
      )
      assertRowsIgnoringOrder(
        table = LIBRARY_BOOK,
        expected = listOf(book),
        connection = connection
      )
    }
  }

  @Test
  fun defaultDowngradeDropsTriggersAndIndexesBeforeCascadingTablesAndRecreatesSearchableIndexes() = withNamedDatabase(
    databaseName = FULL_TEXT_DOWNGRADE_DATABASE_NAME
  ) { application ->
    val database = SqliteMagicDatabase()
    openDatabaseFile(
      application = application,
      databaseName = FULL_TEXT_DOWNGRADE_DATABASE_NAME
    ).use { oldConnection ->
      database.createSchema(oldConnection)
      oldConnection.execSQL("INSERT INTO letter_author VALUES (1, 'Frank')")
      oldConnection.execSQL(
        "INSERT INTO letter (id, subject, body, tag, language, author) VALUES (1, 'discarded dune', NULL, 'old', 0, 1)"
      )
      oldConnection.execSQL("CREATE TABLE obsolete_note (id INTEGER PRIMARY KEY, body TEXT)")
      oldConnection.execSQL("INSERT INTO obsolete_note VALUES (1, 'obsolete dune')")
      oldConnection.execSQL("CREATE VIRTUAL TABLE obsolete_note_fts USING fts4(body)")
      oldConnection.execSQL("INSERT INTO obsolete_note_fts(docid, body) SELECT id, body FROM obsolete_note")
      oldConnection.execSQL("PRAGMA user_version = ${database.dbVersion + 1}")
    }

    openNamedConnection(
      application = application,
      databaseName = FULL_TEXT_DOWNGRADE_DATABASE_NAME,
      database = database
    ).use { connection ->
      val tables = schemaObjectNames(
        master = SQLITE_MASTER,
        type = "table",
        connection = connection
      )
      assertThat(tables).containsAtLeast("letter", "letter_fts", "letter_fts_content", "letter_fts_segdir")
      assertThat(tables.filter { it.orEmpty().startsWith("obsolete_note") }).isEmpty()

      Letter(
        id = 2,
        subject = "Dune",
        body = null,
        tag = "recreated",
        language = 0,
        author = LetterAuthor(
          id = 1,
          name = "Frank"
        )
      )
        .insert()
        .usingConnection(connection)
        .execute()
      assertThat(
        SELECT
          .COLUMN(LETTER.ID)
          .FROM(LETTER)
          .WHERE(LETTER_FTS MATCH "dune")
          .usingConnection(connection)
          .execute() to LETTER_FTS
          .integrityCheck()
          .usingConnection(connection)
          .execute()
      ).isEqualTo(listOf(2L) to 1)
    }
  }
}
