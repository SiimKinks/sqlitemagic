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
   * Log warning messages.
   *
   * @param message Loggable message
   */
  fun logWarning(message: String)

  /**
   * Log error messages.
   *
   * @param message Loggable message
   */
  fun logError(message: String)

  /**
   * Log error messages.
   *
   * @param message Loggable message
   * @param throwable Error causing throwable
   */
  fun logError(message: String, throwable: Throwable)

  /**
   * Log query execution time.
   *
   * @param queryTimeInMillis Query execution time in milliseconds
   * @param observedTables Tables that are observed by the query
   * @param sql Query SQL
   * @param args Optional query arguments; individual values may be null for SQL NULL.
   */
  fun logQueryTime(
    queryTimeInMillis: Long,
    observedTables: Array<String>,
    sql: String,
    args: Array<out String?>?
  )
}
