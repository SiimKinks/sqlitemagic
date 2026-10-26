package com.siimkinks.sqlitemagic.entity

import android.database.SQLException
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Unique
import io.reactivex.Completable

/**
 * Builder for a bulk update operation that identifies each entity row by a provided unique column.
 *
 * @param P Parent table type
 */
interface EntityBulkUpdateByColumnBuilder<P> : EntityOperationBuilder<EntityBulkUpdateByColumnBuilder<P>> {
  /**
   * Execute this configured bulk update operation against a database using the provided column
   * to identify each entity row.
   * Non-recursive operations and recursive operations without `CONFLICT_IGNORE` execute inside one transaction.
   * Recursive operations using `CONFLICT_IGNORE` execute each entity graph in its own transaction.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return `true` if at least one entity graph committed; `false` when no entity graph committed.
   *     With `CONFLICT_IGNORE`, ignored recursive graphs are rolled back independently and successful graphs remain
   *     committed.
   * @throws SQLException when SQLite rejects a write, for example on a constraint violation; the operation rolls
   *     back as above
   */
  fun execute(byColumn: Unique<P>): Boolean

  /**
   * Creates a [Completable] that when subscribed to executes this configured bulk update operation
   * against a database using the provided column to identify each entity row and emits operation result to downstream.
   * Non-recursive operations and recursive operations without `CONFLICT_IGNORE` execute inside one transaction.
   * Recursive operations using `CONFLICT_IGNORE` execute each entity graph in its own transaction. If the operation was
   * successful then complete will be emitted to downstream. An error rolls back the active transaction; recursive
   * `CONFLICT_IGNORE` graphs that committed before a later error or cancellation remain committed.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(byColumn: Unique<P>): Completable
}
