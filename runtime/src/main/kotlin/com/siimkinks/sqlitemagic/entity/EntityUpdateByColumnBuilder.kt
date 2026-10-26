package com.siimkinks.sqlitemagic.entity

import android.database.SQLException
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Unique
import io.reactivex.Completable

/**
 * Builder for an update operation that identifies each entity row by a provided unique column.
 *
 * @param P Parent table type
 */
interface EntityUpdateByColumnBuilder<P> : EntityOperationBuilder<EntityUpdateByColumnBuilder<P>> {
  /**
   * Execute this configured update operation against a database using the provided column
   * to identify each entity row. Operation will be executed inside a transaction if the updated
   * entity has complex columns which also need to be updated.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return `true` if the operation was successful; `false` when no row was updated and the operation was rolled
   *     back
   * @throws SQLException when SQLite rejects the update, for example on a constraint violation
   */
  fun execute(byColumn: Unique<P>): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured update operation
   * against a database using the provided column to identify each entity row. Operation will be
   * executed inside a transaction if the updated entity has complex columns which also need to be
   * updated. If the operation was successful then complete will be emitted to downstream. If the
   * operation failed then it will be rolled back and error will be emitted to downstream.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(byColumn: Unique<P>): Completable
}
