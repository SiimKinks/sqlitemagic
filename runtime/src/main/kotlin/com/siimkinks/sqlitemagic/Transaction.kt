package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.util.concurrent.TimeUnit

/** An in-progress database transaction. */
interface Transaction : Closeable {
  /**
   * End a transaction. See [DbConnection.newTransaction] for usage and commit/rollback behavior.
   *
   * @see SQLiteDatabase.endTransaction
   */
  fun end()

  /**
   * Mark the current transaction successful. Do not do more database work between this call and
   * [end], and do as little non-database work as possible. Errors encountered between this call
   * and [end] do not prevent the transaction from being committed.
   *
   * @see SQLiteDatabase.setTransactionSuccessful
   */
  fun markSuccessful()

  /**
   * Temporarily end the transaction to let other threads run. The transaction is assumed successful
   * so far. Do not call [markSuccessful] first. A new transaction is created when this returns,
   * but is not marked successful. This assumes no nested transactions ([DbConnection.newTransaction]
   * has been called only once) and throws if that is not the case.
   *
   * @return true if the transaction was yielded
   * @see SQLiteDatabase.yieldIfContendedSafely
   */
  fun yieldIfContendedSafely(): Boolean

  /**
   * Temporarily end the transaction to let other threads run. The transaction is assumed successful
   * so far. Do not call [markSuccessful] first. A new transaction is created when this returns,
   * but is not marked successful. This assumes no nested transactions ([DbConnection.newTransaction]
   * has been called only once) and throws if that is not the case.
   *
   * @param sleepAmount If greater than zero, sleep this long before restarting a transaction when
   * the lock was yielded, allowing other background threads to make more progress.
   * @return true if the transaction was yielded
   * @see SQLiteDatabase.yieldIfContendedSafely
   */
  fun yieldIfContendedSafely(
    sleepAmount: Long,
    sleepUnit: TimeUnit
  ): Boolean

  /** Equivalent to calling [end]. */
  override fun close()
}
