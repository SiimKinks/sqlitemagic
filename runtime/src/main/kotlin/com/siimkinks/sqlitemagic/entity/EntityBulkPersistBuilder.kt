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
   * Non-recursive operations and recursive operations without `CONFLICT_IGNORE` execute inside one transaction.
   * Recursive operations using `CONFLICT_IGNORE` execute each entity graph in its own transaction.
   *
   * @return `true` if at least one entity graph committed; `false` when no entity graph committed.
   * With `CONFLICT_IGNORE`, ignored recursive graphs are rolled back independently and successful
   * graphs remain committed.
   */
  fun execute(): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured bulk persist operation
   * against a database and emits operation result to downstream. Non-recursive operations and recursive operations
   * without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE` execute
   * each entity graph in its own transaction. If the operation was successful then complete will be emitted to
   * downstream. An error rolls back the active transaction; recursive `CONFLICT_IGNORE` graphs that committed
   * before a later error or cancellation remain committed.
   *
   * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Completable
}
