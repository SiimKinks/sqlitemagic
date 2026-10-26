package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Unique
import io.reactivex.Single

/**
 * Builder for a delete operation that identifies an entity row by a provided unique column.
 *
 * @param P Parent table type
 */
interface EntityDeleteByColumnBuilder<P> : ConnectionProvidedOperation<EntityDeleteByColumnBuilder<P>> {
  /**
   * Execute this configured delete operation against a database using the provided column to identify the entity row.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return Nr of deleted records
   */
  fun execute(byColumn: Unique<P>): Int

  /**
   * Creates a [Single] that when subscribed to executes this configured delete operation against a
   * database using the provided column to identify the entity row and emits nr of deleted records
   * to downstream only once.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(byColumn: Unique<P>): Single<Int>
}
