package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteConnection

@InternalSqliteMagicApi
interface GeneratedSchema {
  fun createSchemaTables(connection: SQLiteConnection, temporary: Boolean)

  fun collectGeneratedViews(views: MutableList<GeneratedView>)

  fun createSchemaIndexes(connection: SQLiteConnection, temporary: Boolean)

  fun clearTables(connection: SQLiteConnection, changedTables: MutableScatterSet<String>)
}
