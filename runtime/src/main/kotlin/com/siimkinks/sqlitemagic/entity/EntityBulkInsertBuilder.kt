package com.siimkinks.sqlitemagic.entity

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.annotation.CheckResult
import io.reactivex.Completable

/**
 * Builder for bulk insert operation.
 */
interface EntityBulkInsertBuilder : EntityOperationBuilder<EntityBulkInsertBuilder> {
  /**
   * Execute this configured bulk insert operation against a database.
   * Non-recursive operations and recursive operations without [CONFLICT_IGNORE] execute inside one transaction.
   * Recursive operations using [CONFLICT_IGNORE] execute each entity graph in its own transaction.
   *
   * @return `true` if at least one entity graph committed; `false` when no entity graph committed.
   *
   * If [CONFLICT_IGNORE] is used, returns `true` if at
   * least one operation was successful and table change trigger(s) got sent; `false` when all
   * operations were ignored and no table change trigger(s) got sent. For recursive operations, an
   * entity graph that committed before a later error or cancellation remains committed.
   */
  fun execute(): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured bulk insert operation
   * against a database and emits operation result to downstream. Non-recursive operations and recursive operations
   * without [CONFLICT_IGNORE] execute inside one transaction. Recursive operations using [CONFLICT_IGNORE] execute
   * each entity graph in its own transaction. If the operation was successful then complete will be emitted to
   * downstream. An error rolls back the active transaction; recursive [CONFLICT_IGNORE] graphs that committed
   * before a later error or cancellation remain committed.
   *
   * @return Deferred [Completable] that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @CheckResult
  fun observe(): Completable
}
