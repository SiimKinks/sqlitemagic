package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import io.reactivex.Completable;

/**
 * Builder for bulk insert operation.
 *
 * @param <T> Operation target type
 */
public interface EntityBulkInsertBuilder<T> extends ConnectionProvidedOperation<EntityBulkInsertBuilder<T>> {
  /**
   * Execute this configured bulk insert operation against a database.
   * Operation will be executed inside a transaction.
   *
   * @return {@code true} if the operation was successful; {@code false} when some operation failed
   * and this operation was rolled back.
   */
  boolean execute();

  /**
   * Creates a {@link Completable} that when subscribed to executes this configured bulk
   * insert operation against a database and emits operation result to downstream.
   * Operation will be executed inside a transaction. If the operation was
   * successful then complete will be emitted to downstream. If the operation failed then it
   * will be rolled back and error will be emitted to downstream.
   *
   * @return Deferred {@link Completable} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  Completable observe();
}
