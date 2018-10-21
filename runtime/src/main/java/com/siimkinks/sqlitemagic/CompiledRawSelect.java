package com.siimkinks.sqlitemagic;

import android.database.Cursor;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Immutable object that contains raw SQL SELECT statement. This object
 * can be shared between threads without any side effects.
 * <p>
 * Note: This class does not contain SQL statement compiled against database.
 */
public interface CompiledRawSelect {
  /**
   * Execute this raw SELECT statement against a database.
   * <p>
   * This method runs synchronously in the calling thread.
   *
   * @return {@link Cursor} over the result set
   */
  @NonNull
  @CheckResult
  @WorkerThread
  Cursor execute();
}
