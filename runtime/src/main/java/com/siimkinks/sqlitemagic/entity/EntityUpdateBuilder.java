package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.ConflictAlgorithm;

import io.reactivex.Completable;

/**
 * Builder for update operation.
 */
public interface EntityUpdateBuilder extends ConnectionProvidedOperation<EntityUpdateBuilder> {
  /**
   * Configure this operation to use provided conflict algorithm.
   *
   * @param conflictAlgorithm One of {@link android.database.sqlite.SQLiteDatabase}
   *                          CONFLICT_* constant values
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  EntityUpdateBuilder conflictAlgorithm(@ConflictAlgorithm int conflictAlgorithm);

  /**
   * Execute this configured update operation against a database.
   * Operation will be executed inside a transaction if the updated entity has complex columns
   * which also need to be updated.
   *
   * @return {@code true} if the operation was successful; {@code false} when operation failed
   * and it was rolled back.
   */
  boolean execute();

  /**
   * Creates a {@link Completable} that when subscribed to executes this configured
   * update operation against a database and emits the operation result to downstream.
   * Operation will be executed inside a transaction if the updated entity has
   * complex columns which also need to be updated.
   * If the operation was successful then complete will be emitted to downstream.
   * If the operation failed then it will be rolled back and error will be emitted to downstream.
   *
   * @return Deferred {@link Completable} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  Completable observe();
}
