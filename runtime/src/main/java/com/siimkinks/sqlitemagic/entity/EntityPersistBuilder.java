package com.siimkinks.sqlitemagic.entity;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import com.siimkinks.sqlitemagic.exception.OperationFailedException;

import io.reactivex.Single;

/**
 * Builder for persist operation.
 * <p>
 * Persist is an operation that first tries to update and if that fails then inserts
 * the provided entity.
 */
public interface EntityPersistBuilder extends EntityPersistOperationBuilder<EntityPersistBuilder>,
    EntityOperationByColumnBuilder<EntityPersistBuilder> {
  /**
   * Execute this configured persist operation against a database.
   * Operation will be executed inside a transaction if the persisted entity has complex columns
   * which also need to be persisted.
   * <p>
   * Returns {@link EntityPersistResult.Inserted} when a new row was inserted,
   * {@link EntityPersistResult.Updated} when an existing row was updated, or
   * {@link EntityPersistResult.Ignored} when the configured conflict algorithm intentionally
   * ignored the write. A failure that was not intentionally ignored throws
   * {@link OperationFailedException}.
   *
   * @return The persist operation result
   */
  @NonNull
  EntityPersistResult execute();

  /**
   * Creates a {@link Single} that when subscribed to executes this configured
   * persist operation against a database and emits the operation result to downstream
   * only once. Operation will be executed inside a transaction if the persisted entity has
   * complex columns which also need to be persisted.
   * If the operation was successful or intentionally ignored then its result will be emitted to
   * downstream. If the operation failed then it will be rolled back and error will be emitted to
   * downstream. See {@link #execute()} for the result cases.
   *
   * @return Deferred {@link Single} that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @NonNull
  @CheckResult
  Single<EntityPersistResult> observe();
}
