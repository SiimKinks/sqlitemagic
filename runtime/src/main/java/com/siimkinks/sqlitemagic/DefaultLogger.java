package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Arrays;

/**
 * The default logger implementation.
 */
public class DefaultLogger implements Logger {

  static final String TAG_SQLITE_MAGIC = "SqliteMagic";
  static final String TAG_DATABASE = "DATABASE: ";

  @Override
  public void logDebug(@NonNull String message) {
    Log.d(TAG_SQLITE_MAGIC, TAG_DATABASE + message);
  }

  @Override
  public void logWarning(@NonNull String message) {
    Log.w(TAG_SQLITE_MAGIC, TAG_DATABASE + message);
  }

  @Override
  public void logError(@NonNull String message) {
    Log.e(TAG_SQLITE_MAGIC, TAG_DATABASE + message);
  }

  @Override
  public void logError(@NonNull String message, @NonNull Throwable throwable) {
    Log.e(TAG_SQLITE_MAGIC, TAG_DATABASE + message, throwable);
  }

  @Override
  public void logQueryTime(long queryTimeInMillis, @NonNull String[] observedTables, @NonNull String sql, @Nullable String[] args) {
    final String logMsg = String.format("%s QUERY (%sms)\n  tables: %s\n  sql: %s\n  args: %s",
        TAG_DATABASE,
        queryTimeInMillis,
        Arrays.toString(observedTables),
        sql,
        Arrays.toString(args));
    Log.d(TAG_SQLITE_MAGIC, logMsg);
  }
}
