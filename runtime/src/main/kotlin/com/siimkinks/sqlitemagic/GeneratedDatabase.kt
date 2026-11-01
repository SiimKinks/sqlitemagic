package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
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
  fun applySchemaPragmas(connection: SQLiteConnection)

  @InternalSqliteMagicApi
  fun createSchema(connection: SQLiteConnection)

  @InternalSqliteMagicApi
  fun createTemporarySchema(connection: SQLiteConnection) = Unit

  @InternalSqliteMagicApi
  fun clearData(connection: SQLiteConnection): MutableScatterSet<String>

  @InternalSqliteMagicApi
  fun migrateViews(connection: SQLiteConnection)

  @InternalSqliteMagicApi
  fun getNrOfTables(moduleName: String?): Int
}
