package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.ConflictAlgorithm;

import io.reactivex.Single;

/**
 * Builder for insert operation.
 */
public interface EntityInsertBuilder extends ConnectionProvidedOperation<EntityInsertBuilder> {
  /**
   * Configure this operation to use provided conflict algorithm.
   *
   * @param conflictAlgorithm One of {@link android.database.sqlite.SQLiteDatabase}
   *                          CONFLICT_* constant values
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  EntityInsertBuilder conflictAlgorithm(@ConflictAlgorithm int conflictAlgorithm);

  /**
   * Execute this configured insert operation against a database.
   * Operation will be executed inside a transaction if the inserted entity has complex columns
   * which also need to be inserted.
   *
   * @return the row ID of the newly inserted row, or -1 if an error occurred
   */
  long execute();

  /**
   * Creates a {@link Single} that when subscribed to executes this configured
   * insert operation against a database and emits operation result to downstream
   * only once. Operation will be executed inside a transaction
   * if the inserted entity has complex columns which also need to be inserted.
   * If the operation was successful then the row ID of the newly inserted row
   * will be emitted to downstream. If the operation failed then it will be rolled back
   * and error will be emitted to downstream.
   *
   * @return Deferred {@link Single} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  Single<Long> observe();
}
