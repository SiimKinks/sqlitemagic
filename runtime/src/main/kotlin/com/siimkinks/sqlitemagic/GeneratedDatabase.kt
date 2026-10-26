package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.MutableScatterSet

interface GeneratedDatabase {
  @InternalSqliteMagicApi
  val dbName: String?

  @InternalSqliteMagicApi
  val dbVersion: Int

  @InternalSqliteMagicApi
  val submoduleNames: Array<String>?

  @InternalSqliteMagicApi
  val isDebug: Boolean

  @InternalSqliteMagicApi
  fun configureDatabase(db: SupportSQLiteDatabase)

  @InternalSqliteMagicApi
  fun createSchema(db: SupportSQLiteDatabase)

  @InternalSqliteMagicApi
  fun createTemporarySchema(db: SupportSQLiteDatabase) = Unit

  @InternalSqliteMagicApi
  fun clearData(db: SupportSQLiteDatabase): MutableScatterSet<String>

  @InternalSqliteMagicApi
  fun migrateViews(db: SupportSQLiteDatabase)

  @InternalSqliteMagicApi
  fun getNrOfTables(moduleName: String?): Int
}
