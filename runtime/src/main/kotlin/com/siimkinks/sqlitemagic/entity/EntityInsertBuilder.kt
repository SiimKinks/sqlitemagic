package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.entity.EntityInsertResult.Ignored
import com.siimkinks.sqlitemagic.entity.EntityInsertResult.Inserted
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Single

/**
 * Builder for insert operation.
 */
interface EntityInsertBuilder : EntityOperationBuilder<EntityInsertBuilder> {
  /**
   * Execute this configured insert operation against a database. Operation will be executed inside
   * a transaction if the inserted entity has complex columns which also need to be inserted.
   *
   * @return [Inserted] when a row was inserted or [Ignored] when a conflict was intentionally ignored
   * @throws [OperationFailedException] when the insert fails without being intentionally ignored
   */
  fun execute(): EntityInsertResult

  /**
   * Creates a [Single] that when subscribed to executes this configured insert operation against a
   * database and emits operation result to downstream only once. Operation will be executed inside
   * a transaction if the inserted entity has complex columns which also need to be inserted. If
   * the operation was successful or intentionally ignored then its result will be emitted. If the
   * operation failed then it will be rolled back and an error will be emitted.
   *
   * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
   */
  @CheckResult
  fun observe(): Single<EntityInsertResult>
}
