package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import rx.Single;

/**
 * Builder for persist operation.
 * <p>
 * Persist is an operation that first tries to update and if that fails then inserts
 * the provided entity.
 */
public interface EntityPersistBuilder extends ConnectionProvidedOperation<EntityPersistBuilder> {
  /**
   * Configure this operation to ignore {@code null} values inside entity while
   * persisting provided object.
   *
   * @return Operation builder
   */
  @NonNull
  @CheckResult
  EntityPersistBuilder ignoreNullValues();

  /**
   * Execute this configured persist operation against a database.
   * Operation will be executed inside a transaction if the persisted entity has complex columns
   * which also need to be persisted.
   *
   * @return the row ID of the updated or newly inserted row, or -1 if insert operation was
   * performed and an error occurred
   */
  long execute();

  /**
   * Creates a {@link Single} that when subscribed to executes this configured
   * persist operation against a database and emits the operation result to downstream
   * only once. Operation will be executed inside a transaction if the persisted entity has
   * complex columns which also need to be persisted.
   * If the operation was successful then the row ID of the updated or newly inserted row
   * will be emitted to downstream. If the operation failed then it will be rolled
   * back and error will be emitted to downstream.
   *
   * @return Deferred {@link Single} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  Single<Long> observe();
}
