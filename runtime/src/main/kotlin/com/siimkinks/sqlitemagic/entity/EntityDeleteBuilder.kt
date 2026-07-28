package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import io.reactivex.Single

/**
 * Builder for delete operation.
 */
interface EntityDeleteBuilder : ConnectionProvidedOperation<EntityDeleteBuilder> {
  /**
   * Execute this configured delete operation against a database.
   *
   * @return Nr of deleted records
   */
  fun execute(): Int

  /**
   * Creates a [Single] that when subscribed to executes this configured delete operation against a
   * database and emits nr of deleted records to downstream only once.
   *
   * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Single<Int>
}
