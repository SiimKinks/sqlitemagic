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
   * Operation will be executed inside a transaction.
   *
   * @return `true` if the operation was successful; `false` when some operation failed
   * and this operation was rolled back.
   *
   * If [CONFLICT_IGNORE] is used, returns `true` if at
   * least one operation was successful and table change trigger(s) got sent; `false` when all
   * operations failed and no table change trigger(s) got sent.
   */
  fun execute(): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured bulk insert operation
   * against a database and emits operation result to downstream. Operation will be executed inside
   * a transaction. If the operation was successful then complete will be emitted to downstream. If
   * the operation failed then it will be rolled back and error will be emitted to downstream.
   *
   * @return Deferred [Completable] that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @CheckResult
  fun observe(): Completable
}
