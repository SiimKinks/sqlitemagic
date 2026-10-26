package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.fixture.model.TemporaryAccountEntry
import com.siimkinks.sqlitemagic.fixture.model.TemporaryWithoutRowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.catalog.SchemaOptionModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_TEMP_MASTER
import com.siimkinks.sqlitemagic.runtime.support.insertedPersistResult
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.reopenDefaultConnection
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectSql
import org.junit.Rule
import org.junit.Test

internal class SchemaOptionRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun temporaryWithoutRowIdTableUsesTemporarySchemaAndReopensEmpty() {
    val tableName = SchemaOptionModelCatalog.temporaryWithoutRowIdTableName
    assertThat(
      schemaObjectNames(
        master = SQLITE_TEMP_MASTER,
        type = "table"
      )
    ).contains(tableName)
    assertThat(
      schemaObjectNames(
        master = SQLITE_MASTER,
        type = "table"
      )
    ).doesNotContain(tableName)

    val value = TemporaryWithoutRowIdEntity(
      id = "temporary-without-rowid-id",
      value = "temporary-without-rowid-value"
    )
    assertThat(
      value
        .insert()
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(rowId = null))
    assertThat(
      SELECT
        .FROM(SchemaOptionModelCatalog.temporaryWithoutRowIdTable)
        .execute()
    ).containsExactly(value)

    reopenDefaultConnection()

    assertThat(
      SELECT
        .FROM(SchemaOptionModelCatalog.temporaryWithoutRowIdTable)
        .execute()
    ).isEmpty()
  }

  @Test
  fun temporaryShallowRelationshipHasNoCascadeAndReopensWithoutTemporaryRow() {
    val account = Account(id = AccountId("temporary-account-id"))
    insertedResult(
      result = account
        .insert()
        .execute(),
      modelName = "Account"
    )

    val entry = TemporaryAccountEntry(
      id = "temporary-account-entry-id",
      account = account,
      value = "temporary-account-entry-value"
    )
    insertedResult(
      result = entry
        .insert()
        .execute(),
      modelName = "TemporaryAccountEntry"
    )
    assertThat(
      SELECT
        .FROM(SchemaOptionModelCatalog.temporaryAccountEntryTable)
        .execute()
    ).containsExactly(
      TemporaryAccountEntry(
        id = entry.id,
        account = Account(id = AccountId("temporary-account-id")),
        value = entry.value
      )
    )

    val tableSql = schemaObjectSql(
      master = SQLITE_TEMP_MASTER,
      type = "table",
      name = SchemaOptionModelCatalog.temporaryAccountEntryTableName
    )
    assertThat(tableSql).doesNotContain("REFERENCES")
    assertThat(tableSql).doesNotContain("ON DELETE CASCADE")

    reopenDefaultConnection()

    assertThat(
      SELECT
        .FROM(SchemaOptionModelCatalog.temporaryAccountEntryTable)
        .execute()
    ).isEmpty()
    assertThat(
      SELECT
        .FROM(ACCOUNT)
        .execute()
    ).containsExactly(account)
  }

  @Test
  fun customSchemaAndDefaultAreUsedByNullOmittingPersist() {
    val schema = schemaObjectSql(
      master = SQLITE_MASTER,
      type = "table",
      name = SchemaOptionModelCatalog.libraryBookTableName
    )
    assertThat(schema).contains("CREATE TABLE library_books")
    assertThat(schema).contains("book_key TEXT PRIMARY KEY")
    assertThat(schema).contains("title_text TEXT DEFAULT 'untitled'")

    insertedPersistResult(
      result = LibraryBook(
        id = "library-book-id",
        title = null
      )
        .persist()
        .ignoreNullValues()
        .execute(),
      modelName = "LibraryBook"
    )
    assertThat(
      SELECT
        .FROM(SchemaOptionModelCatalog.libraryBookTable)
        .execute()
    ).containsExactly(
      LibraryBook(
        id = "library-book-id",
        title = "untitled"
      )
    )
  }
}
