package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.content.Context
import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.mainSchemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Test

private const val DOWNGRADE_DATABASE_NAME = "schema-downgrade-runtime-test.db"

class SchemaDowngradeRuntimeTest {
  // Database lifecycle and schema replacement require reopening a named database, outside shared operation contracts.
  @Test
  fun defaultDowngradeReplacesApplicationTablesAndRecreatesUsableGeneratedSchema() = withNamedDatabase(
    databaseName = DOWNGRADE_DATABASE_NAME
  ) { application ->
    val database = SqliteMagicDatabase()
    val version = database.dbVersion
    val metadata = application
      .openOrCreateDatabase(
        DOWNGRADE_DATABASE_NAME,
        Context.MODE_PRIVATE,
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
      assertThat(
        Select
          .raw("PRAGMA user_version")
          .from(ANONYMOUS_TABLE)
          .usingConnection(connection)
          .execute()
          .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
          }
      ).isEqualTo(version)
      val tables = mainSchemaObjectNames(
        connection = connection,
        type = "table"
      )
      assertThat(tables).containsAtLeast("library_books", "android_metadata", "sqlite_sequence")
      assertThat(tables).doesNotContain("obsolete_application_table")
      assertThat(
        Select
          .raw("SELECT locale FROM android_metadata ORDER BY locale")
          .from(ANONYMOUS_TABLE)
          .usingConnection(connection)
          .execute()
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
      assertSeedInserted(
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
