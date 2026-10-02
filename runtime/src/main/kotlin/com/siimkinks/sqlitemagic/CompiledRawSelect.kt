package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread

/**
 * Immutable object that contains raw SQL SELECT statement. This object can be shared between threads without any side
 * effects.
 *
 * Note: This class does not contain SQL statement compiled against database.
 */
interface CompiledRawSelect {
  /**
   * Execute this raw SELECT statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return [Cursor] over the result set
   */
  @CheckResult
  @WorkerThread
  fun execute(): Cursor
}
