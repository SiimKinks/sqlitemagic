package com.siimkinks.sqlitemagic.runtime.contract.manager

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
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
}
