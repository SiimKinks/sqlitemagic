package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import io.reactivex.Single

/**
 * Builder for insert operation.
 */
interface EntityInsertBuilder : EntityOperationBuilder<EntityInsertBuilder> {
  /**
   * Execute this configured insert operation against a database. Operation will be executed inside
   * a transaction if the inserted entity has complex columns which also need to be inserted.
   *
   * @return the row ID of the newly inserted row, or -1 if an error occurred
   */
  fun execute(): Long

  /**
   * Creates a [Single] that when subscribed to executes this configured insert operation against a
   * database and emits operation result to downstream only once. Operation will be executed inside
   * a transaction if the inserted entity has complex columns which also need to be inserted. If
   * the operation was successful then the row ID of the newly inserted row will be emitted to
   * downstream. If the operation failed then it will be rolled back and error will be emitted to
   * downstream.
   *
   * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Single<Long>
}
