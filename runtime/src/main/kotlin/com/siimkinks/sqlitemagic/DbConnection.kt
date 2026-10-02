package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import java.io.Closeable

/** Database connection reference. */
interface DbConnection : Closeable {
  /** Close the underlying SQLite connection and release all cached resources. */
  override fun close()

  /**
   * Begin a transaction for this thread.
   *
   * Transactions may nest. If no transaction is in progress, a database connection is obtained
   * and a new transaction is started. Otherwise, a nested transaction is started.
   *
   * Each call to [newTransaction] must be matched exactly by a call to [Transaction.end]. Call
   * [Transaction.markSuccessful] before ending a transaction to mark it successful. If a transaction
   * or any nested transaction is unsuccessful, the entire transaction rolls back when the outermost
   * transaction ends.
   *
   * Transactions queue query notifications until they have been applied.
   *
   * Use [use] to end the transaction automatically:
   * ```kotlin
   * db.newTransaction().use { transaction ->
   *   // Perform database work.
   *   transaction.markSuccessful()
   * }
   * ```
   *
   * Alternatively, end it explicitly:
   * ```kotlin
   * val transaction = db.newTransaction()
   * try {
   *   // Perform database work.
   *   transaction.markSuccessful()
   * } finally {
   *   transaction.end()
   * }
   * ```
   *
   * @return New transaction object
   * @see android.database.sqlite.SQLiteDatabase.beginTransaction
   */
  @CheckResult
  fun newTransaction(): Transaction

  /** Clear all data in tables. */
  @WorkerThread
  fun clearData()
}
