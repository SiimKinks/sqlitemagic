package com.siimkinks.sqlitemagic.framework

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.ReadOnlyConnections
import java.io.FileInputStream
import java.io.IOException

/**
 * [SQLiteDriver] for the SQLite library of the Android framework.
 *
 * Each connection is one framework [SQLiteDatabase] connection. Query results are read directly from the framework's
 * cursor windows, and the framework's typed exceptions, Android collations and Database Inspector support remain.
 * Corruption fails with [SQLiteDatabaseCorruptException] and leaves the database files in place. Transaction statements
 * run on the connection rather than in the framework's per-thread session, so as with bundled SQLite, a transaction
 * belongs to the connection instead of the thread that began it, and a `COMMIT` that fails keeps its transaction open
 * unless SQLite rolled it back. A row larger than a cursor window fails with [SQLiteException]. A statement with a
 * `RETURNING` clause runs again for each further cursor window of rows it returns. Each connection takes the default
 * locale when it opens, so after the locale changes, connections that open later sort `LOCALIZED` text by the new
 * locale. A database file in WAL mode stays in WAL mode when a connection opens: a short-lived read-only connection
 * holds the WAL while the framework sets its default journal mode, so the framework logs that it could not change the
 * journal mode because the database is locked.
 *
 * Example:
 * ```
 * SqliteMagic.builder(this)
 *     .database(SqliteMagicDatabase())
 *     .driver(FrameworkSQLiteDriver())
 *     .openDefaultConnection()
 * ```
 */
class FrameworkSQLiteDriver : SQLiteDriver, ReadOnlyConnections {
  override fun open(fileName: String): SQLiteConnection {
    val walReader = openWalReader(fileName)
    return try {
      open(
        fileName = fileName,
        flags = CREATE_IF_NECESSARY
      )
    } finally {
      walReader?.close()
    }
  }

  @InternalSqliteMagicApi
  override fun openReadOnly(fileName: String): SQLiteConnection = open(
    fileName = fileName,
    flags = OPEN_READONLY
  )

  private fun open(fileName: String, flags: Int) = FrameworkSQLiteConnection(
    openDatabase(fileName, null, flags, KEEP_CORRUPT_DATABASE).apply {
      if (SDK_INT >= UPSIDE_DOWN_CAKE) {
        disableWriteAheadLogging()
      }
    }
  )
}

private fun openWalReader(fileName: String): SQLiteDatabase? {
  if (!isWalDatabase(fileName)) {
    return null
  }
  val reader = try {
    openDatabase(fileName, null, OPEN_READONLY or NO_LOCALIZED_COLLATORS, KEEP_CORRUPT_DATABASE)
  } catch (_: SQLiteException) {
    return null
  }
  return try {
    reader
        .compileStatement("SELECT count(*) FROM sqlite_master")
        .use(SQLiteStatement::simpleQueryForLong)
    reader
  } catch (_: SQLiteException) {
    reader.close()
    null
  }
}

private fun isWalDatabase(fileName: String): Boolean {
  val header = ByteArray(SQLITE_HEADER_READ_VERSION + 1)
  return try {
    FileInputStream(fileName).use { it.read(header) } == header.size &&
        header[SQLITE_HEADER_WRITE_VERSION] == WAL_FILE_FORMAT &&
        header[SQLITE_HEADER_READ_VERSION] == WAL_FILE_FORMAT
  } catch (_: IOException) {
    false
  }
}

private val KEEP_CORRUPT_DATABASE = DatabaseErrorHandler {}
private const val SQLITE_HEADER_WRITE_VERSION = 18
private const val SQLITE_HEADER_READ_VERSION = 19
private const val WAL_FILE_FORMAT: Byte = 2
