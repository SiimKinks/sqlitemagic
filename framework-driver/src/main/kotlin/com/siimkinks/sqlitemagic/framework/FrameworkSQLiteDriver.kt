package com.siimkinks.sqlitemagic.framework

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver

/**
 * [SQLiteDriver] for the SQLite library of the Android framework.
 *
 * Each connection is one framework [SQLiteDatabase] connection. Query results are read directly from the framework's
 * cursor windows, and the framework's typed exceptions, Android collations and Database Inspector support remain.
 * Corruption fails with [SQLiteDatabaseCorruptException] and leaves the database files in place. A `COMMIT` that fails
 * rolls its transaction back, because the framework ends its own transaction state before committing; bundled SQLite
 * keeps such a transaction open. A row larger than a cursor window fails with [SQLiteException]. A statement with a
 * `RETURNING` clause runs again for each further cursor window of rows it returns. Each connection takes the default
 * locale when it opens, so after the locale changes, connections that open later sort `LOCALIZED` text by the new
 * locale.
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
    openDatabase(fileName, null, CREATE_IF_NECESSARY, KEEP_CORRUPT_DATABASE).apply {
      if (SDK_INT >= UPSIDE_DOWN_CAKE) {
        disableWriteAheadLogging()
      }
    }
  )
}

private val KEEP_CORRUPT_DATABASE = DatabaseErrorHandler {}
