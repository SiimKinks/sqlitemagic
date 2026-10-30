package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Native results of mutation statements.
 *
 * A [SQLiteConnection] implements this when its engine reports the row ID and change count of a mutation without an
 * extra query. Other connections get them from SQLite's `changes()` and `last_insert_rowid()`.
 */
@InternalSqliteMagicApi
interface MutationResults {
  /**
   * Execute an INSERT statement prepared on this connection.
   *
   * @param statement Statement prepared on this connection, with its values bound
   * @return Row ID of the inserted row, or `-1` when no row was inserted
   */
  fun executeInsert(statement: SQLiteStatement): Long

  /**
   * Execute an UPDATE or DELETE statement prepared on this connection.
   *
   * @param statement Statement prepared on this connection, with its values bound
   * @return Number of changed rows
   */
  fun executeUpdateDelete(statement: SQLiteStatement): Int
}
