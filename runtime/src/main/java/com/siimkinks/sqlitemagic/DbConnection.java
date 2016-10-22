package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import java.io.Closeable;

/**
 * Database connection reference.
 */
public interface DbConnection extends Closeable {
  /**
   * Close the underlying SQLite connection and release all cached resources.
   */
  void close();

  /**
   * Begin a transaction for this thread.
   * <p>
   * Transactions may nest. If the transaction is not in progress, then a database connection is
   * obtained and a new transaction is started. Otherwise, a nested transaction is started.
   * <p>
   * Each call to {@code newTransaction} must be matched exactly by a call to
   * {@link Transaction#end()}. To mark a transaction as successful, call
   * {@link Transaction#markSuccessful()} before calling {@link Transaction#end()}. If the
   * transaction is not successful, or if any of its nested transactions were not successful, then
   * the entire transaction will be rolled back when the outermost transaction is ended.
   * <p>
   * Transactions queue up all query notifications until they have been applied.
   * <p>
   * Here is the standard idiom for transactions:
   * <pre>{@code
   * try (Transaction transaction = db.newTransaction()) {
   *   ...
   *   transaction.markSuccessful();
   * }
   * }</pre>
   * <p>
   * Manually call {@link Transaction#end()} when try-with-resources is not available:
   * <pre>{@code
   * Transaction transaction = db.newTransaction();
   * try {
   *   ...
   *   transaction.markSuccessful();
   * } finally {
   *   transaction.end();
   * }
   * }</pre>
   *
   * @return New transaction object
   * @see android.database.sqlite.SQLiteDatabase#beginTransaction()
   */
  @NonNull
  @CheckResult
  Transaction newTransaction();
}
