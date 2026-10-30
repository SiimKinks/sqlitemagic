package com.siimkinks.sqlitemagic.coroutines

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
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeferredOperation
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive

/**
 * Execute this configured insert operation against a database and return its result. Operation will be executed inside
 * a transaction if the inserted entity has complex columns which also need to be inserted. If the operation failed then
 * it will be rolled back and its exception will be thrown.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityInsertBuilder.execute] returns
 */
suspend fun EntityInsertBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured bulk insert operation against a database. Non-recursive operations and recursive operations
 * without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE` execute each
 * entity graph in its own transaction. An error or cancellation of the calling coroutine rolls back the active
 * transaction; recursive `CONFLICT_IGNORE` graphs that committed before a later error or cancellation remain committed.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityBulkInsertBuilder.execute] returns
 */
suspend fun EntityBulkInsertBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured update operation against a database. Operation will be executed inside a transaction if the
 * updated entity has complex columns which also need to be updated. If the operation failed then it will be rolled back
 * and its exception will be thrown.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityUpdateBuilder.execute] returns
 */
suspend fun EntityUpdateBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured update operation against a database using the provided column to identify the entity row.
 * Operation will be executed inside a transaction if the updated entity has complex columns which also need to be
 * updated. If the operation failed then it will be rolled back and its exception will be thrown.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return What [EntityUpdateByColumnBuilder.execute] returns
 */
suspend fun <P> EntityUpdateByColumnBuilder<P>.await(byColumn: Unique<P>) = ReactiveAdapterSupport
  .deferred(
    builder = this,
    byColumn = byColumn
  )
  .awaitResult()

/**
 * Execute this configured bulk update operation against a database. Non-recursive operations and recursive operations
 * without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE` execute each
 * entity graph in its own transaction. An error or cancellation of the calling coroutine rolls back the active
 * transaction; recursive `CONFLICT_IGNORE` graphs that committed before a later error or cancellation remain committed.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityBulkUpdateBuilder.execute] returns
 */
suspend fun EntityBulkUpdateBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured bulk update operation against a database using the provided column to identify each entity
 * row. Non-recursive operations and recursive operations without `CONFLICT_IGNORE` execute inside one transaction.
 * Recursive operations using `CONFLICT_IGNORE` execute each entity graph in its own transaction. An error or
 * cancellation of the calling coroutine rolls back the active transaction; recursive `CONFLICT_IGNORE` graphs that
 * committed before a later error or cancellation remain committed.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return What [EntityBulkUpdateByColumnBuilder.execute] returns
 */
suspend fun <P> EntityBulkUpdateByColumnBuilder<P>.await(byColumn: Unique<P>) = ReactiveAdapterSupport
  .deferred(
    builder = this,
    byColumn = byColumn
  )
  .awaitResult()

/**
 * Execute this configured persist operation against a database and return its result. Operation will be executed inside
 * a transaction if the persisted entity has complex columns which also need to be persisted. If the operation failed
 * then it will be rolled back and its exception will be thrown.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityPersistBuilder.execute] returns
 */
suspend fun EntityPersistBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured persist operation against a database using the provided column to identify the entity row and
 * return its result. Operation will be executed inside a transaction if the persisted entity has complex columns which
 * also need to be persisted. If the operation failed then it will be rolled back and its exception will be thrown.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return What [EntityPersistByColumnBuilder.execute] returns
 */
suspend fun <P> EntityPersistByColumnBuilder<P>.await(byColumn: Unique<P>) = ReactiveAdapterSupport
  .deferred(
    builder = this,
    byColumn = byColumn
  )
  .awaitResult()

/**
 * Execute this configured bulk persist operation against a database. Non-recursive operations and recursive operations
 * without `CONFLICT_IGNORE` execute inside one transaction. Recursive operations using `CONFLICT_IGNORE` execute each
 * entity graph in its own transaction. An error or cancellation of the calling coroutine rolls back the active
 * transaction; recursive `CONFLICT_IGNORE` graphs that committed before a later error or cancellation remain committed.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityBulkPersistBuilder.execute] returns
 */
suspend fun EntityBulkPersistBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured bulk persist operation against a database using the provided column to identify each entity
 * row. Non-recursive operations and recursive operations without `CONFLICT_IGNORE` execute inside one transaction.
 * Recursive operations using `CONFLICT_IGNORE` execute each entity graph in its own transaction. An error or
 * cancellation of the calling coroutine rolls back the active transaction; recursive `CONFLICT_IGNORE` graphs that
 * committed before a later error or cancellation remain committed.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return What [EntityBulkPersistByColumnBuilder.execute] returns
 */
suspend fun <P> EntityBulkPersistByColumnBuilder<P>.await(byColumn: Unique<P>) = ReactiveAdapterSupport
  .deferred(
    builder = this,
    byColumn = byColumn
  )
  .awaitResult()

/**
 * Execute this configured delete operation against a database and return the nr of deleted records.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityDeleteBuilder.execute] returns
 */
suspend fun EntityDeleteBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured delete operation against a database using the provided column to identify the entity row and
 * return the nr of deleted records.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return What [EntityDeleteByColumnBuilder.execute] returns
 */
suspend fun <P> EntityDeleteByColumnBuilder<P>.await(byColumn: Unique<P>) = ReactiveAdapterSupport
  .deferred(
    builder = this,
    byColumn = byColumn
  )
  .awaitResult()

/**
 * Execute this configured bulk delete operation against a database and return the nr of deleted records.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityBulkDeleteBuilder.execute] returns
 */
suspend fun EntityBulkDeleteBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

/**
 * Execute this configured bulk delete operation against a database using the provided column to identify each entity
 * row and return the nr of deleted records.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param byColumn Generated non-null unique column of the table for this operation
 * @return What [EntityBulkDeleteByColumnBuilder.execute] returns
 */
suspend fun <P> EntityBulkDeleteByColumnBuilder<P>.await(byColumn: Unique<P>) = ReactiveAdapterSupport
  .deferred(
    builder = this,
    byColumn = byColumn
  )
  .awaitResult()

/**
 * Execute this configured table delete operation against a database and return the nr of deleted records.
 *
 * The operation runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return What [EntityDeleteTableBuilder.execute] returns
 */
suspend fun EntityDeleteTableBuilder.await() = ReactiveAdapterSupport
  .deferred(this)
  .awaitResult()

private suspend fun <T> DeferredOperation<T>.awaitResult() = ReactiveAdapterSupport
  .connection(this)
  .withQueryDispatcher { execute(isCancelled = { !isActive }) }
