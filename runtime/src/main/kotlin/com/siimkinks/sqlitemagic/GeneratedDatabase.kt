package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.siimkinks.sqlitemagic.internal.StringArraySet

interface GeneratedDatabase {
  val dbName: String?
  val dbVersion: Int
  val submoduleNames: Array<String>?
  val isDebug: Boolean
  fun configureDatabase(db: SupportSQLiteDatabase)
  fun createSchema(db: SupportSQLiteDatabase)
  fun createTemporarySchema(db: SupportSQLiteDatabase) = Unit
  fun clearData(db: SupportSQLiteDatabase): StringArraySet?
  fun migrateViews(db: SupportSQLiteDatabase)
  fun getNrOfTables(moduleName: String?): Int
  fun <V : Any> columnForValue(input: V): Column<V, V, V, *, NotNullable>
}
