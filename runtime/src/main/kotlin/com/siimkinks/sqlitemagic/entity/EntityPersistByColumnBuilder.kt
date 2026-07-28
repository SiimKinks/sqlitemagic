package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Ignored
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Inserted
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Updated
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Single

/**
 * Builder for a persist operation that identifies an existing entity row by a provided unique column.
 *
 * Persist is an operation that first tries to update and if that fails then inserts the provided entity.
 *
 * @param P Parent table type
 */
interface EntityPersistByColumnBuilder<P> : EntityPersistOperationBuilder<EntityPersistByColumnBuilder<P>> {
  /**
   * Execute this configured persist operation against a database using the provided column
   * to identify the entity row. Operation will be executed inside a transaction if the persisted
   * entity has complex columns which also need to be persisted.
   *
   * Returns [Inserted] when a new row was inserted, [Updated] when an existing row was updated, or
   * [Ignored] when the configured conflict algorithm intentionally ignored
   * the write. A failure that was not intentionally ignored throws [OperationFailedException].
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param C Not nullable unique column type
   * @return The persist operation result
   */
  fun <C> execute(byColumn: C): EntityPersistResult
      where C : Column<*, *, *, P, NotNullable>,
            C : Unique<NotNullable>

  /**
   * Creates a [Single] that when subscribed to executes this configured persist operation against a
   * database using the provided column to identify the entity row. Operation will be executed
   * inside a transaction if the persisted entity has complex columns which also need to be
   * persisted. If the operation was successful or intentionally ignored then its result will be
   * emitted to downstream. If the operation failed then it will be rolled back and error will be
   * emitted to downstream.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param C Not nullable unique column type
   * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun <C> observe(byColumn: C): Single<EntityPersistResult>
      where C : Column<*, *, *, P, NotNullable>,
            C : Unique<NotNullable>
}
