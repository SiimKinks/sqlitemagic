package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection

/** Database downgrading callback. */
fun interface DbDowngrader {
  /**
   * Downgrade the database when its version is higher than the version of the generated database.
   *
   * This runs in an exclusive transaction that sets the database version to [newVersion] after this returns. An
   * exception rolls the transaction back and fails opening the database.
   *
   * @param connection Connection to the database being downgraded
   * @param oldVersion Current database version
   * @param newVersion Version of the generated database
   */
  fun onDowngrade(connection: SQLiteConnection, oldVersion: Int, newVersion: Int)
}
