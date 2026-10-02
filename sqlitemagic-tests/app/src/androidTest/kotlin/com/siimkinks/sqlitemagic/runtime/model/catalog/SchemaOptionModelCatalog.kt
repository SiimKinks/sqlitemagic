package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.TemporaryAccountEntryTable.Companion.TEMPORARY_ACCOUNT_ENTRY
import com.siimkinks.sqlitemagic.TemporaryWithoutRowIdEntityTable.Companion.TEMPORARY_WITHOUT_ROW_ID_ENTITY

internal object SchemaOptionModelCatalog {
  val temporaryWithoutRowIdTable = TEMPORARY_WITHOUT_ROW_ID_ENTITY
  val temporaryAccountEntryTable = TEMPORARY_ACCOUNT_ENTRY
  val libraryBookTable = LIBRARY_BOOK

  const val temporaryWithoutRowIdTableName = "temporary_without_row_id_entity"
  const val temporaryAccountEntryTableName = "temporary_account_entry"
  val temporaryTableNames = listOf(
    temporaryWithoutRowIdTableName,
    temporaryAccountEntryTableName
  )

  const val libraryBookTableName = "library_books"
}
