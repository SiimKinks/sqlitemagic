package com.siimkinks.sqlitemagic.rxjava2

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeferredCompletion
import com.siimkinks.sqlitemagic.internal.DeferredOperation
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.Completable
import io.reactivex.Single

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
fun EntityInsertBuilder.observe(): Single<EntityInsertResult> = ReactiveAdapterSupport
  .deferred(this)
  .observeSingle()

/**
 * Creates a [Completable] that when subscribed to executes this configured bulk insert operation
 * against a database and emits operation result to downstream. Non-recursive operations and recursive
 * operations without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE`
 * execute each entity graph in its own transaction. If the operation was successful then complete will be emitted to
 * downstream. An error rolls back the active transaction; recursive `CONFLICT_IGNORE` graphs that committed before a
 * later error or cancellation remain committed.
 *
 * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityBulkInsertBuilder.observe(): Completable = ReactiveAdapterSupport
  .deferred(this)
  .observeBulk()

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
fun EntityUpdateBuilder.observe(): Completable = ReactiveAdapterSupport
  .deferred(this)
  .observeUpdate()

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
fun <P> EntityUpdateByColumnBuilder<P>.observe(byColumn: Unique<P>): Completable =
  ReactiveAdapterSupport
    .deferred(
      builder = this,
      byColumn = byColumn
    )
    .observeUpdate()

/**
 * Creates a [Completable] that when subscribed to executes this configured bulk update operation
 * against a database and emits operation result to downstream. Non-recursive operations and recursive
 * operations without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE`
 * execute each entity graph in its own transaction. If the operation was successful then complete will be emitted to
 * downstream. An error rolls back the active transaction; recursive `CONFLICT_IGNORE` graphs that committed before a
 * later error or cancellation remain committed.
 *
 * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityBulkUpdateBuilder.observe(): Completable = ReactiveAdapterSupport
  .deferred(this)
  .observeBulk()

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
fun <P> EntityBulkUpdateByColumnBuilder<P>.observe(byColumn: Unique<P>): Completable =
  ReactiveAdapterSupport
    .deferred(
      builder = this,
      byColumn = byColumn
    )
    .observeBulk()

/**
 * Creates a [Single] that when subscribed to executes this configured persist operation against a
 * database and emits the operation result to downstream only once. Operation will be executed
 * inside a transaction if the persisted entity has complex columns which also need to be
 * persisted. If the operation was successful or intentionally ignored then its result will be
 * emitted to downstream. If the operation failed then it will be rolled back and error will be
 * emitted to downstream. See [EntityPersistBuilder.execute] for the result cases.
 *
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityPersistBuilder.observe(): Single<EntityPersistResult> = ReactiveAdapterSupport
  .deferred(this)
  .observeSingle()

/**
 * Creates a [Single] that when subscribed to executes this configured persist operation against a
 * database using the provided column to identify the entity row. Operation will be executed
 * inside a transaction if the persisted entity has complex columns which also need to be
 * persisted. If the operation was successful or intentionally ignored then its result will be
 * emitted to downstream. If the operation failed then it will be rolled back and error will be
 * emitted to downstream.
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun <P> EntityPersistByColumnBuilder<P>.observe(byColumn: Unique<P>): Single<EntityPersistResult> =
  ReactiveAdapterSupport
    .deferred(
      builder = this,
      byColumn = byColumn
    )
    .observeSingle()

/**
 * Creates a [Completable] that when subscribed to executes this configured bulk persist operation
 * against a database and emits operation result to downstream. Non-recursive operations and recursive
 * operations without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE`
 * execute each entity graph in its own transaction. If the operation was successful then complete will be emitted to
 * downstream. An error rolls back the active transaction; recursive `CONFLICT_IGNORE` graphs that committed before a
 * later error or cancellation remain committed.
 *
 * @return Deferred [Completable] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityBulkPersistBuilder.observe(): Completable = ReactiveAdapterSupport
  .deferred(this)
  .observeBulk()

/**
 * Creates a [Completable] that when subscribed to executes this configured bulk persist operation
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
fun <P> EntityBulkPersistByColumnBuilder<P>.observe(byColumn: Unique<P>): Completable =
  ReactiveAdapterSupport
    .deferred(
      builder = this,
      byColumn = byColumn
    )
    .observeBulk()

/**
 * Creates a [Single] that when subscribed to executes this configured delete operation against a
 * database and emits nr of deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityDeleteBuilder.observe(): Single<Int> = ReactiveAdapterSupport
  .deferred(this)
  .observeSingle()

/**
 * Creates a [Single] that when subscribed to executes this configured delete operation against a
 * database using the provided column to identify the entity row and emits nr of deleted records
 * to downstream only once.
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun <P> EntityDeleteByColumnBuilder<P>.observe(byColumn: Unique<P>): Single<Int> =
  ReactiveAdapterSupport
    .deferred(
      builder = this,
      byColumn = byColumn
    )
    .observeSingle()

/**
 * Creates a [Single] that when subscribed to executes this configured bulk delete operation
 * against a database and emits nr of deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityBulkDeleteBuilder.observe(): Single<Int> = ReactiveAdapterSupport
  .deferred(this)
  .observeSingle()

/**
 * Creates a [Single] that when subscribed to executes this configured bulk delete operation
 * against a database using the provided column to identify each entity row and emits nr of
 * deleted records to downstream only once.
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun <P> EntityBulkDeleteByColumnBuilder<P>.observe(byColumn: Unique<P>): Single<Int> =
  ReactiveAdapterSupport
    .deferred(
      builder = this,
      byColumn = byColumn
    )
    .observeSingle()

/**
 * Creates a [Single] that when subscribed to executes this configured table delete operation
 * against a database and emits nr of deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the operation and emits its result to downstream
 */
@CheckResult
fun EntityDeleteTableBuilder.observe(): Single<Int> = ReactiveAdapterSupport
  .deferred(this)
  .observeSingle()

internal fun <T : Any> DeferredOperation<T>.observeSingle(): Single<T> = Single.fromCallable {
  execute(isCancelled = ::neverCancelled)
}

internal fun DeferredCompletion.observeUpdate(): Completable = Completable.fromAction {
  complete(isCancelled = ::neverCancelled)?.let { throw it }
}

internal fun DeferredCompletion.observeBulk(): Completable = Completable.create { emitter ->
  when (val failure = complete(isCancelled = emitter::isDisposed)) {
    null -> emitter.onComplete()
    else -> emitter.tryOnError(failure)
  }
}

private fun neverCancelled() = false
