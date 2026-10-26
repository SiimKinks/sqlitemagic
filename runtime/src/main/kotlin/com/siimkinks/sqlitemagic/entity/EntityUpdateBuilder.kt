package com.siimkinks.sqlitemagic.entity

import android.database.SQLException
import androidx.annotation.CheckResult
import io.reactivex.Completable

/**
 * Builder for update operation.
 */
interface EntityUpdateBuilder :
  EntityOperationBuilder<EntityUpdateBuilder>,
  EntityOperationByColumnBuilder<EntityUpdateBuilder> {
  /**
   * Execute this configured update operation against a database. Operation will be executed inside
   * a transaction if the updated entity has complex columns which also need to be updated.
   *
   * @return `true` if the operation was successful; `false` when no row was updated and the operation was rolled
   *     back
   * @throws SQLException when SQLite rejects the update, for example on a constraint violation
   */
  fun execute(): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured update operation
   * against a database and emits the operation result to downstream. Operation will be executed
   * inside a transaction if the updated entity has complex columns which also need to be updated.
   * If the operation was successful then complete will be emitted to downstream. If the operation
   * failed then it will be rolled back and error will be emitted to downstream.
   *
   * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Completable
}
