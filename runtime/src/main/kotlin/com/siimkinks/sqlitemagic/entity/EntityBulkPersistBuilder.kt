package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import io.reactivex.Completable

/**
 * Builder for bulk persist operation.
 *
 * Persist is an operation that first tries to update and if that fails then inserts the provided entity.
 */
interface EntityBulkPersistBuilder :
  EntityPersistOperationBuilder<EntityBulkPersistBuilder>,
  EntityOperationByColumnBuilder<EntityBulkPersistBuilder> {
  /**
   * Execute this configured bulk persist operation against a database.
   * Operation will be executed inside a transaction.
   *
   * @return `true` if the operation was successful; `false` when some operation failed
   * and this operation was rolled back.
   */
  fun execute(): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured bulk persist operation
   * against a database and emits operation result to downstream. Operation will be executed inside
   * a transaction. If the operation was successful then complete will be emitted to downstream.
   * If the operation failed then it will be rolled back and error will be emitted to downstream.
   *
   * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Completable
}
