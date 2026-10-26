package com.siimkinks.sqlitemagic

/** A simple indirection for logging debug messages. */
interface Logger {
  /**
   * Log debug messages.
   *
   * @param message Loggable message
   */
  fun logDebug(message: String)

  /**
   * Log error messages.
   *
   * @param message Loggable message
   * @param throwable Error causing throwable
   */
  fun logError(message: String, throwable: Throwable)
}
