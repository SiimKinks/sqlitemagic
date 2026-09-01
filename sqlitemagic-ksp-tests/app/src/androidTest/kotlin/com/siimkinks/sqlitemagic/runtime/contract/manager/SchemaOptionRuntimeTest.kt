package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.database.Cursor
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.TestApp
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.fixture.model.TemporaryAccountEntry
import com.siimkinks.sqlitemagic.fixture.model.TemporaryWithoutRowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.catalog.SchemaOptionModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

private const val SQLITE_MASTER = "sqlite_master"
private const val SQLITE_TEMP_MASTER = "sqlite_temp_master"

class SchemaOptionRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun temporaryWithoutRowIdTableUsesTemporarySchemaAndReopensEmpty() {
    val tableName = SchemaOptionModelCatalog.temporaryWithoutRowIdTableName
    assertThat(
      tableExists(
        master = SQLITE_TEMP_MASTER,
        tableName = tableName
      )
    ).isTrue()
    assertThat(
      tableExists(
        master = SQLITE_MASTER,
        tableName = tableName
      )
    ).isFalse()

    val value = TemporaryWithoutRowIdEntity(
      id = "temporary-without-rowid-id",
      value = "temporary-without-rowid-value"
    )
    val result = value
      .insert()
      .execute()
    val inserted = result as? EntityInsertResult.Inserted
      ?: throw AssertionError("Temporary WITHOUT ROWID insert was not inserted: $result")
    assertThat(inserted.rowId).isNull()
    assertThat(
      Select
        .from(SchemaOptionModelCatalog.temporaryWithoutRowIdTable)
        .execute()
    ).containsExactly(value)

    reopenDefaultConnection()

    assertThat(
      Select
        .from(SchemaOptionModelCatalog.temporaryWithoutRowIdTable)
        .execute()
    ).isEmpty()
  }

  @Test
  fun temporaryShallowRelationshipHasNoCascadeAndReopensWithoutTemporaryRow() {
    val account = Account(id = AccountId("temporary-account-id"))
    assertThat(
      account
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

    val entry = TemporaryAccountEntry(
      id = "temporary-account-entry-id",
      account = account,
      value = "temporary-account-entry-value"
    )
    assertThat(
      entry
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)
    assertThat(
      Select
        .from(SchemaOptionModelCatalog.temporaryAccountEntryTable)
        .execute()
    ).containsExactly(
      TemporaryAccountEntry(
        id = entry.id,
        account = Account(id = AccountId("temporary-account-id")),
        value = entry.value
      )
    )

    val tableSql = tableSql(
      master = SQLITE_TEMP_MASTER,
      tableName = SchemaOptionModelCatalog.temporaryAccountEntryTableName
    )
    assertThat(tableSql).doesNotContain("REFERENCES")
    assertThat(tableSql).doesNotContain("ON DELETE CASCADE")

    reopenDefaultConnection()

    assertThat(
      Select
        .from(SchemaOptionModelCatalog.temporaryAccountEntryTable)
        .execute()
    ).isEmpty()
    assertThat(
      Select
        .from(ACCOUNT)
        .execute()
    ).containsExactly(account)
  }

  @Test
  fun customSchemaAndDefaultAreUsedByNullOmittingPersist() {
    val schema = tableSql(
      master = SQLITE_MASTER,
      tableName = SchemaOptionModelCatalog.libraryBookTableName
    )
    assertThat(schema).contains("CREATE TABLE library_books")
    assertThat(schema).contains("book_key TEXT PRIMARY KEY")
    assertThat(schema).contains("title_text TEXT DEFAULT 'untitled'")

    val result = LibraryBook(
      id = "library-book-id",
      title = null
    )
      .persist()
      .ignoreNullValues()
      .execute()
    assertThat(result)
      .isInstanceOf(EntityPersistResult.Inserted::class.java)
    assertThat(
      Select
        .from(SchemaOptionModelCatalog.libraryBookTable)
        .execute()
    ).containsExactly(
      LibraryBook(
        id = "library-book-id",
        title = "untitled"
      )
    )
  }

  private fun tableExists(
    master: String,
    tableName: String
  ) = Select
    .raw("SELECT name FROM $master WHERE type = 'table' AND name = ?")
    .from(SIMPLE_MUTABLE_ENTITY)
    .withArgs(tableName)
    .execute()
    .use(Cursor::hasRow)

  private fun tableSql(
    master: String,
    tableName: String
  ) = Select
    .raw("SELECT sql FROM $master WHERE type = 'table' AND name = ?")
    .from(SIMPLE_MUTABLE_ENTITY)
    .withArgs(tableName)
    .execute()
    .use(Cursor::readSingleString)

  private fun reopenDefaultConnection() {
    val application = InstrumentationRegistry
      .getInstrumentation()
      .targetContext
      .applicationContext as TestApp
    application.initDb(app = application)
  }
}

private fun Cursor.hasRow() = moveToFirst()

private fun Cursor.readSingleString(): String {
  check(moveToFirst())
  return getString(0)
}
