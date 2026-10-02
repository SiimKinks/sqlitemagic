package com.siimkinks.sqlitemagic

import android.util.Log
import com.siimkinks.sqlitemagic.SqliteMagic.logger

internal const val TAG_SQLITE_MAGIC = "SqliteMagic"

/** Internal logging utility. */
object LogUtil {
  fun logInfo(msg: String, vararg args: Any?) {
    Log.i(TAG_SQLITE_MAGIC, msg.formatMessage(args))
  }

  fun logMandatoryWarning(msg: String, vararg args: Any?) {
    Log.w(TAG_SQLITE_MAGIC, msg.formatMessage(args))
  }

  fun logQueryTime(
    queryTimeInMillis: Long,
    observedTables: Array<String>,
    sql: String,
    args: Array<out String?>?
  ) {
    logger?.logQueryTime(
      queryTimeInMillis = queryTimeInMillis,
      observedTables = observedTables,
      sql = sql,
      args = args
    )
  }

  fun logDebug(msg: String, vararg args: Any?) {
    logger?.logDebug(msg.formatMessage(args))
  }

  fun logWarning(msg: String, vararg args: Any?) {
    logger?.logWarning(msg.formatMessage(args))
  }

  fun logError(msg: String, vararg args: Any?) {
    logger?.logError(msg.formatMessage(args))
  }

  fun logError(e: Exception, msg: String, vararg args: Any?) {
    logger?.logError(
      message = msg.formatMessage(args),
      throwable = e
    )
  }

  private fun String.formatMessage(args: Array<out Any?>) = when {
    args.isEmpty() -> this
    else -> format(*args)
  }
}
