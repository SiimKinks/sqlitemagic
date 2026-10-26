package com.siimkinks.sqlitemagic.internal

import android.util.Log
import com.siimkinks.sqlitemagic.SqliteMagic
import java.util.concurrent.CancellationException

internal const val TAG_SQLITE_MAGIC = "SqliteMagic"

internal object LogUtil {
  fun logInfo(message: String) {
    Log.i(TAG_SQLITE_MAGIC, message)
  }

  inline fun logDebug(message: () -> String) {
    if (SqliteMagic.loggingEnabled) {
      SqliteMagic.logger?.logDebug(message())
    }
  }

  inline fun logError(exception: Throwable, message: () -> String) {
    if (SqliteMagic.loggingEnabled) {
      SqliteMagic.logger?.logError(
        message = message(),
        throwable = exception
      )
    }
  }

  inline fun <T> loggingFailure(message: String, block: () -> T): T = try {
    block()
  } catch (exception: CancellationException) {
    throw exception
  } catch (exception: Exception) {
    logError(exception) { message }
    throw exception
  }
}
