package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import io.reactivex.Completable;

/**
 * Builder for bulk insert operation.
 */
public interface EntityBulkInsertBuilder extends EntityOperationBuilder<EntityBulkInsertBuilder> {
  /**
   * Execute this configured bulk insert operation against a database.
   * Operation will be executed inside a transaction.
   *
   * @return {@code true} if the operation was successful; {@code false} when some operation failed
   * and this operation was rolled back.
   * <p>
   * If {@link android.database.sqlite.SQLiteDatabase#CONFLICT_IGNORE CONFLICT_IGNORE} is used,
   * returns {@code true} if at least one operation was successful and table change trigger(s) got
   * sent; {@code false} when all operations failed and no table change trigger(s) got sent.
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
