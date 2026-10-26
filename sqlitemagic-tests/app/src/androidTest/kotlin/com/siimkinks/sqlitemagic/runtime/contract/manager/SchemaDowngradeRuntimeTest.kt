package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.content.Context.MODE_PRIVATE
import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.readableDatabase
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Test

private const val DOWNGRADE_DATABASE_NAME = "schema-downgrade-runtime-test.db"

internal class SchemaDowngradeRuntimeTest {
  @Test
  fun defaultDowngradeReplacesApplicationTablesAndRecreatesUsableGeneratedSchema() = withNamedDatabase(
    databaseName = DOWNGRADE_DATABASE_NAME
  ) { application ->
    val database = SqliteMagicDatabase()
    val version = database.dbVersion
    val metadata = application
      .openOrCreateDatabase(
        DOWNGRADE_DATABASE_NAME,
        MODE_PRIVATE,
        null
      )
      .use { oldDatabase ->
        oldDatabase.execSQL("CREATE TABLE library_books (book_key TEXT PRIMARY KEY, title_text TEXT)")
        oldDatabase.execSQL("INSERT INTO library_books VALUES ('old-book', 'discarded-title')")
        oldDatabase.execSQL("CREATE TABLE obsolete_application_table (id INTEGER PRIMARY KEY AUTOINCREMENT)")
        oldDatabase.execSQL("INSERT INTO obsolete_application_table DEFAULT VALUES")
        oldDatabase.version = version + 1
        oldDatabase
          .rawQuery("SELECT locale FROM android_metadata ORDER BY locale", null)
          .use(Cursor::readStrings)
      }

    openNamedConnection(
      application = application,
      databaseName = DOWNGRADE_DATABASE_NAME,
      database = database
    ).use { connection ->
      assertThat(connection.readableDatabase.version).isEqualTo(version)
      val tables = schemaObjectNames(
        master = SQLITE_MASTER,
        type = "table",
        database = connection.readableDatabase
      )
      assertThat(tables).containsAtLeast("library_books", "android_metadata", "sqlite_sequence")
      assertThat(tables).doesNotContain("obsolete_application_table")
      assertThat(
        connection
          .readableDatabase
          .query("SELECT locale FROM android_metadata ORDER BY locale")
          .use(Cursor::readStrings)
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
