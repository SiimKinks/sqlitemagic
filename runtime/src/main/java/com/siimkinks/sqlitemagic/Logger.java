package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A simple indirection for logging debug messages.
 */
public interface Logger {
  /**
   * Log debug messages.
   *
   * @param message Loggable message
   */
  void logDebug(@NonNull String message);

  /**
   * Log warning messages.
   *
   * @param message Loggable message
   */
  void logWarning(@NonNull String message);

  /**
   * Log error messages.
   *
   * @param message Loggable message
   */
  void logError(@NonNull String message);

  /**
   * Log error messages.
   *
   * @param message   Loggable message
   * @param throwable Error causing throwable
   */
  void logError(@NonNull String message, @NonNull Throwable throwable);

  /**
   * Log query execution time.
   *
   * @param queryTimeInMillis Query execution time in milliseconds
   * @param observedTables    Tables that are observed by the query
   * @param sql               Query SQL
   * @param args              Query arguments
   */
  void logQueryTime(long queryTimeInMillis, @NonNull String[] observedTables, @NonNull String sql, @Nullable String[] args);
}
