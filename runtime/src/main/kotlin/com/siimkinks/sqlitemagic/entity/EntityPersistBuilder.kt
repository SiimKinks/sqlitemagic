package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Ignored
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Inserted
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Updated
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Single

/**
 * Builder for persist operation.
 *
 * Persist is an operation that first tries to update and if that fails then inserts the provided entity.
 */
interface EntityPersistBuilder :
  EntityPersistOperationBuilder<EntityPersistBuilder>,
  EntityOperationByColumnBuilder<EntityPersistBuilder> {
  /**
   * Execute this configured persist operation against a database. Operation will be executed inside
   * a transaction if the persisted entity has complex columns which also need to be persisted.
   *
   * Returns [Inserted] when a new row was inserted, [Updated] when an existing row was updated, or
   * [Ignored] when the configured conflict algorithm intentionally ignored
   * the write. A failure that was not intentionally ignored throws [OperationFailedException].
   *
   * @return The persist operation result
   */
  fun execute(): EntityPersistResult

  /**
   * Creates a [Single] that when subscribed to executes this configured persist operation against a
   * database and emits the operation result to downstream only once. Operation will be executed
   * inside a transaction if the persisted entity has complex columns which also need to be
   * persisted. If the operation was successful or intentionally ignored then its result will be
   * emitted to downstream. If the operation failed then it will be rolled back and error will be
   * emitted to downstream. See [execute] for the result cases.
   *
   * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Single<EntityPersistResult>
}
