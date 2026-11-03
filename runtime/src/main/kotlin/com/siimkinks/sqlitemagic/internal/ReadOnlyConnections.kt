package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver

/**
 * Read-only connections of a [SQLiteDriver].
 *
 * The connection pool opens its reader connections with [openReadOnly] when its driver implements this, so that
 * opening a reader never writes to the database. Other drivers open readers with [SQLiteDriver.open].
 */
@InternalSqliteMagicApi
interface ReadOnlyConnections {
  /**
   * Open a read-only connection to an existing database.
   *
   * @param fileName Path of the database file
   * @return Connection that cannot write to the database
   */
  fun openReadOnly(fileName: String): SQLiteConnection
}
