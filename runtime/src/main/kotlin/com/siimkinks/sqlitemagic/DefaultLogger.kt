package com.siimkinks.sqlitemagic

import android.util.Log

private const val TAG_DATABASE = "DATABASE: "

/** The default logger implementation. */
open class DefaultLogger : Logger {
  override fun logDebug(message: String) {
    Log.d(TAG_SQLITE_MAGIC, TAG_DATABASE + message)
  }

  override fun logWarning(message: String) {
    Log.w(TAG_SQLITE_MAGIC, TAG_DATABASE + message)
  }

  override fun logError(message: String) {
    Log.e(TAG_SQLITE_MAGIC, TAG_DATABASE + message)
  }

  override fun logError(message: String, throwable: Throwable) {
    Log.e(TAG_SQLITE_MAGIC, TAG_DATABASE + message, throwable)
  }

  override fun logQueryTime(
    queryTimeInMillis: Long,
    observedTables: Array<String>,
    sql: String,
    args: Array<out String?>?
  ) {
    val logMsg = "%s QUERY (%sms)\n  tables: %s\n  sql: %s\n  args: %s"
      .format(
        TAG_DATABASE,
        queryTimeInMillis,
        observedTables.contentToString(),
        sql,
        args.contentToString()
      )
    Log.d(TAG_SQLITE_MAGIC, logMsg)
  }
}
