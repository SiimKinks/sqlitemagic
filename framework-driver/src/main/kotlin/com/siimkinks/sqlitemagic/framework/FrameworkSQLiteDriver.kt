package com.siimkinks.sqlitemagic.framework

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver

/**
 * [SQLiteDriver] for the SQLite library of the Android framework.
 *
 * Each connection is one framework [SQLiteDatabase] connection. Query results are read directly from the framework's
 * cursor windows, and the framework's typed exceptions, Android collations and Database Inspector support remain.
 * Corruption fails with [SQLiteDatabaseCorruptException] and leaves the database files in place.
 *
 * Example:
 * ```
 * SqliteMagic.builder(this)
 *     .database(SqliteMagicDatabase())
 *     .driver(FrameworkSQLiteDriver())
 *     .openDefaultConnection()
 * ```
 */
class FrameworkSQLiteDriver : SQLiteDriver {
  override fun open(fileName: String): SQLiteConnection = FrameworkSQLiteConnection(
    SQLiteDatabase.openDatabase(fileName, null, CREATE_IF_NECESSARY, KEEP_CORRUPT_DATABASE)
  )
}

private val KEEP_CORRUPT_DATABASE = DatabaseErrorHandler {}
