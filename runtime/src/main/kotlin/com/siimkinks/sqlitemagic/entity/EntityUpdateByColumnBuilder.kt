package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.NotNullable
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
   * @param C Not nullable unique column type
   * @return `true` if the operation was successful; `false` when operation failed and it was rolled back.
   */
  fun <C> execute(byColumn: C): Boolean
      where C : Column<*, *, *, P, NotNullable>,
            C : Unique<NotNullable>

  /**
   * Creates a [Completable] that when subscribed to executes this configured update operation
   * against a database using the provided column to identify each entity row. Operation will be
   * executed inside a transaction if the updated entity has complex columns which also need to be
   * updated. If the operation was successful then complete will be emitted to downstream. If the
   * operation failed then it will be rolled back and error will be emitted to downstream.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @param C Not nullable unique column type
   * @return Deferred [Completable] that when subscribed to executes the operation and emits
   * its result to downstream
   */
  @CheckResult
  fun <C> observe(byColumn: C): Completable
      where C : Column<*, *, *, P, NotNullable>,
            C : Unique<NotNullable>
}
