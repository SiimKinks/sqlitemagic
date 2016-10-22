package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteDatabase;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * An in-progress database transaction.
 */
public interface Transaction extends Closeable {
  /**
   * End a transaction. See {@link SqliteMagic#newTransaction()} for notes about how to use this and when
   * transactions are committed and rolled back.
   *
   * @see SQLiteDatabase#endTransaction()
   */
  void end();

  /**
   * Marks the current transaction as successful. Do not do any more database work between
   * calling this and calling {@link #end()}. Do as little non-database work as possible in that
   * situation too. If any errors are encountered between this and {@link #end()} the transaction
   * will still be committed.
   *
   * @see SQLiteDatabase#setTransactionSuccessful()
   */
  void markSuccessful();

  /**
   * Temporarily end the transaction to let other threads run. The transaction is assumed to be
   * successful so far. Do not call {@link #markSuccessful()} before calling this. When this
   * returns a new transaction will have been created but not marked as successful. This assumes
   * that there are no nested transactions (newTransaction has only been called once) and will
   * throw an exception if that is not the case.
   *
   * @return true if the transaction was yielded
   * @see SQLiteDatabase#yieldIfContendedSafely()
   */
  boolean yieldIfContendedSafely();

  /**
   * Temporarily end the transaction to let other threads run. The transaction is assumed to be
   * successful so far. Do not call {@link #markSuccessful()} before calling this. When this
   * returns a new transaction will have been created but not marked as successful. This assumes
   * that there are no nested transactions (newTransaction has only been called once) and will
   * throw an exception if that is not the case.
   *
   * @param sleepAmount if {@code > 0}, sleep this long before starting a new transaction if
   *                    the lock was actually yielded. This will allow other background threads to make some
   *                    more progress than they would if we started the transaction immediately.
   * @return true if the transaction was yielded
   * @see SQLiteDatabase#yieldIfContendedSafely(long)
   */
  boolean yieldIfContendedSafely(long sleepAmount, TimeUnit sleepUnit);

  /**
   * Equivalent to calling {@link #end()}
   */
  @Override
  void close();
}
