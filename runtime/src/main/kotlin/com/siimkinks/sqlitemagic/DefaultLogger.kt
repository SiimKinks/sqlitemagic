package com.siimkinks.sqlitemagic

import android.util.Log
import com.siimkinks.sqlitemagic.internal.TAG_SQLITE_MAGIC

private const val TAG_DATABASE = "DATABASE: "

/** The default logger implementation. */
open class DefaultLogger : Logger {
  override fun logDebug(message: String) {
    Log.d(TAG_SQLITE_MAGIC, TAG_DATABASE + message)
  }

  override fun logError(message: String, throwable: Throwable) {
    Log.e(TAG_SQLITE_MAGIC, TAG_DATABASE + message, throwable)
  }
}
