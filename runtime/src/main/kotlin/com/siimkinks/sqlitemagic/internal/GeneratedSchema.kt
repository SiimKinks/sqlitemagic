package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.db.SupportSQLiteDatabase

@InternalSqliteMagicApi
interface GeneratedSchema {
  fun createSchemaTables(db: SupportSQLiteDatabase, temporary: Boolean)

  fun collectGeneratedViews(views: MutableList<GeneratedView>)

  fun createSchemaIndexes(db: SupportSQLiteDatabase, temporary: Boolean)

  fun clearTables(db: SupportSQLiteDatabase, changedTables: MutableScatterSet<String>)
}
