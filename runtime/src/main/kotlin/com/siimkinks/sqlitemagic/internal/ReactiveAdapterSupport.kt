package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.CompiledRawFirst
import com.siimkinks.sqlitemagic.CompiledRawSelect
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.NOT_DISPOSED
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SelectPlan
import com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder
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
import com.siimkinks.sqlitemagic.orDefault

/**
 * Hooks that reactive adapters build on.
 *
 * The `deferred` functions create the operation that a builder's `execute` runs, keeping the builder configuration
 * from the time of the call. See [DeferredOperation] and [DeferredCompletion].
 */
@InternalSqliteMagicApi
object ReactiveAdapterSupport {
  /**
   * Create the change notifications of a compiled SELECT statement.
   *
   * @param plan Compiled SELECT statement
   * @return Change notifications of [plan]
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun <T> observation(plan: SelectPlan<T>) = plan.observation()

  /**
   * Read the compiled SELECT statement that runs a raw query.
   *
   * @param query Compiled raw query
   * @return Compiled SELECT statement of [query]
   */
  fun <T> plan(query: CompiledRawSelect<T, *>) = query.plan

  /**
   * Read the compiled SELECT statement that runs a raw first-row query.
   *
   * @param query Compiled raw query
   * @return Compiled SELECT statement of [query]
   */
  fun <T> plan(query: CompiledRawFirst<T, *>) = query.plan

  /**
   * Run a query on its connection and read its result.
   *
   * @param query Query to run
   * @param isDisposed Whether the result is no longer needed, which stops reading the rows of a list early
   * @return Query result
   * @throws IllegalStateException if called inside a transaction
   */
  fun <T> run(
    query: Query<T>,
    isDisposed: () -> Boolean = NOT_DISPOSED
  ) = query.run(
    dbConnection = query.executionConnection(),
    isDisposed = isDisposed
  )

  /**
   * Run a query on its connection and read its result inside the transaction that the calling thread takes part in, as
   * the calls of a coroutine transaction do on every thread where its owner is installed.
   *
   * @param query Query to run
   * @param isDisposed Whether the result is no longer needed, which stops reading the rows of a list early
   * @return Query result
   */
  fun <T> runInTransaction(
    query: Query<T>,
    isDisposed: () -> Boolean = NOT_DISPOSED
  ) = query.run(
    dbConnection = query.resolveConnection(),
    isDisposed = isDisposed
  )

  /**
   * Define the value of an adapter setting for the connections that a setup builder opens.
   *
   * Each connection keeps the values that were set when it was opened.
   *
   * @param builder Database connection configuration builder
   * @param key Setting key
   * @param value Setting value
   * @return Database connection configuration builder
   */
  fun <V : Any> adapterSetting(
    builder: DatabaseSetupBuilder,
    key: AdapterSettingKey<V>,
    value: V
  ) = builder.adapterSetting(
    key = key,
    value = value
  )

  /**
   * Read the value of an adapter setting that a connection was opened with.
   *
   * @param connection Opened connection
   * @param key Setting key
   * @return Setting value or `null` if it was not set when [connection] was opened
   */
  fun <V : Any> adapterSetting(
    connection: DbConnection,
    key: AdapterSettingKey<V>
  ) = (connection as DbConnectionImpl).adapterSetting(key)

  /**
   * Resolve the connection that a query runs on: the one it was configured with, or else the current default
   * connection.
   *
   * @param query Query to run
   * @return Connection of [query]
   */
  fun connection(query: Query<*>): DbConnection = query.resolveConnection()

  /**
   * Resolve the connection that a query runs on, as [connection] does, and check that the calling thread can run the
   * query. Adapters that run the query on another thread call this first, so that the calling thread is rejected as
   * [run] would reject it.
   *
   * @param query Query to run
   * @return Connection of [query]
   * @throws IllegalStateException if called inside a transaction
   */
  fun executionConnection(query: Query<*>): DbConnection = query.executionConnection()

  /**
   * Check whether a connection is closed.
   *
   * @param connection Connection to check
   * @return Whether [connection] is closed
   */
  fun isClosed(connection: DbConnection) = (connection as DbConnectionImpl).isClosed

  /**
   * Resolve the connection that a deferred entity operation runs on: the one its builder was configured with, or
   * else the current default connection.
   *
   * @param operation Operation returned by `deferred`
   * @return Connection of [operation]
   */
  fun connection(operation: DeferredOperation<*>): DbConnection = operation.connection.orDefault()

  /**
   * Resolve the connection that a compiled UPDATE or DELETE statement runs on: the one it was configured with, or
   * else the current default connection.
   *
   * @param mutation Compiled statement
   * @return Connection of [mutation]
   */
  fun connection(mutation: CompiledMutation): DbConnection = mutation.resolveConnection()

  /**
   * Get the owner of a coroutine transaction on a connection, decided by the calling thread: the owner of the coroutine
   * transaction it already runs for, the owner that already continues the thread's own transaction, a new owner that
   * continues the thread's own transaction until every user of it is released, or else a new owner. Begin, mark and end
   * the transaction with [DbConnection.newTransaction] on a thread where the owner is installed, and release the owner
   * with [releaseTransactionOwner] when the coroutine transaction ends.
   *
   * @param connection Connection of the transaction
   * @return Owner to install on every thread that runs the transaction
   */
  fun transactionOwner(connection: DbConnection) = (connection as DbConnectionImpl).pool.shareOwner()

  /**
   * Make an owner hold the leases and transaction of the calling thread until [restoreLeaseOwner].
   *
   * @param owner Owner returned by [transactionOwner]
   * @return Previous owner of the calling thread to pass to [restoreLeaseOwner]
   */
  fun installLeaseOwner(owner: LeaseOwner) = owner.pool.installOwner(owner)

  /**
   * Give the calling thread back the owner that [installLeaseOwner] replaced.
   *
   * @param owner Owner that was installed
   * @param previous Value returned by [installLeaseOwner]
   */
  fun restoreLeaseOwner(owner: LeaseOwner, previous: LeaseOwner?) = owner.pool.restoreOwner(previous)

  /**
   * Get the owner of a coroutine transaction on a connection that is installed on the calling thread. A coroutine that
   * another coroutine resumes without dispatching runs on that coroutine's thread and sees its owner. Calls of such a
   * coroutine that is not part of the transaction replace the owner with `null` through [restoreLeaseOwner] and put it
   * back afterwards.
   *
   * @param connection Connection of the transaction
   * @return Installed owner, or `null` if the calling thread runs no coroutine transaction of [connection]
   */
  fun installedTransactionOwner(connection: DbConnection) = (connection as DbConnectionImpl).pool.installedSharedOwner()

  /**
   * End the use of an owner returned by [transactionOwner]. An owner that continued a transaction of a thread gives
   * that transaction back to the thread. Calls on threads where an ended owner is still installed fail.
   *
   * While the owner continues a thread's transaction, the thread cannot use it. If the thread ends it anyway, the
   * transaction rolls back, the owner's calls fail and so does this release.
   *
   * @param owner Owner returned by [transactionOwner]
   * @throws IllegalStateException if the thread ended the transaction that the owner continued
   */
  fun releaseTransactionOwner(owner: LeaseOwner) = owner.pool.releaseSharedOwner(owner)

  /**
   * Commit a coroutine transaction and release the writer connection if another writer waits for it. Unless this
   * returns `false`, call [resumeTransaction] next. A call of the transaction made before then begins it again first.
   * Ending the transaction before it begins again keeps the segments committed so far.
   *
   * The transaction does not yield when it is nested or after a nested level failed.
   *
   * @param owner Owner of the transaction, whose outermost level is open
   * @return Whether the transaction was committed and the writer released
   */
  fun yieldTransaction(owner: LeaseOwner) = owner.pool.yieldSharedTransaction(owner)

  /**
   * Take the writer connection back and begin the transaction again after [yieldTransaction] returned `true`, unless a
   * call of the transaction already did. If the writer cannot be taken back, this throws and ending the transaction
   * keeps only the segments committed so far.
   *
   * @param owner Owner passed to [yieldTransaction]
   */
  fun resumeTransaction(owner: LeaseOwner) = owner.pool.resumeSharedTransaction(owner)

  /**
   * Begin a coroutine call on the calling thread. Until [endWriterCall], the first wait of the call for the writer
   * connection throws a [WriterWait] instead of blocking the thread. Later waits block, since the call may have made
   * changes by then, but a call outside a coroutine transaction keeps the writer until it ends, so it waits only once.
   *
   * @param connection Connection of the call
   * @param grant Granted wait of the previous run of the call, or `null` on its first run
   * @return Call to pass to [endWriterCall]
   */
  fun beginWriterCall(
    connection: DbConnection,
    grant: WriterWait?
  ) = (connection as DbConnectionImpl).pool.beginWriterCall(grant)

  /**
   * End a call begun with [beginWriterCall] on the same thread, releasing the writer connection that it kept and a
   * grant that it did not use.
   *
   * @param call Value returned by [beginWriterCall]
   */
  fun endWriterCall(call: WriterCall) = call.pool.endWriterCall(call)

  /**
   * Run an action when the writer connection is granted to a wait. The action runs on the thread that releases the
   * writer, inside the release, so it must only schedule the call to run again.
   *
   * @param wait Wait thrown by the call
   * @param onGranted Action to run when the writer is granted
   * @return `true` if the writer was already granted, in which case [onGranted] does not run
   */
  fun awaitWriter(
    wait: WriterWait,
    onGranted: () -> Unit
  ) = wait.pool.awaitWriter(
    wait = wait,
    onGranted = onGranted
  )

  /**
   * Stop a wait for the writer connection, releasing the writer if it was granted and the call did not run again.
   * Further calls do nothing.
   *
   * @param wait Wait thrown by the call
   */
  fun cancelWriterWait(wait: WriterWait) = wait.pool.cancelWriterWait(wait)

  /**
   * Read how long a call may wait for the writer connection before failing with [writerTimeout].
   *
   * @param wait Wait thrown by the call
   * @return Pool timeout of the connection
   */
  fun writerWaitTimeout(wait: WriterWait) = wait.pool.poolTimeout

  /**
   * Create the failure of a call that waited for the writer connection longer than [writerWaitTimeout].
   *
   * @param wait Wait thrown by the call
   * @return Failure naming the holder of the writer, as a blocking wait reports it
   */
  fun writerTimeout(wait: WriterWait) = wait.pool.writerTimeout()

  fun deferred(builder: EntityInsertBuilder) = (builder as InsertBuilder<*>).deferred()

  fun deferred(builder: EntityBulkInsertBuilder) = (builder as BulkInsertBuilder<*>).deferred()

  fun deferred(builder: EntityUpdateBuilder) = (builder as UpdateBuilder<*>).deferred()

  fun <P> deferred(
    builder: EntityUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = (builder as UpdateByColumnBuilder<P>).deferred(byColumn)

  fun deferred(builder: EntityBulkUpdateBuilder) = (builder as BulkUpdateBuilder<*>).deferred()

  fun <P> deferred(
    builder: EntityBulkUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = (builder as BulkUpdateByColumnBuilder<P>).deferred(byColumn)

  fun deferred(builder: EntityPersistBuilder) = (builder as PersistBuilder<*>).deferred()

  fun <P> deferred(
    builder: EntityPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = (builder as PersistByColumnBuilder<P>).deferred(byColumn)

  fun deferred(builder: EntityBulkPersistBuilder) = (builder as BulkPersistBuilder<*>).deferred()

  fun <P> deferred(
    builder: EntityBulkPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = (builder as BulkPersistByColumnBuilder<P>).deferred(byColumn)

  fun deferred(builder: EntityDeleteBuilder) = (builder as DeleteBuilder<*>).deferred()

  fun <P> deferred(
    builder: EntityDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = (builder as DeleteByColumnBuilder<P>).deferred(byColumn)

  fun deferred(builder: EntityBulkDeleteBuilder) = (builder as BulkDeleteBuilder<*>).deferred()

  fun <P> deferred(
    builder: EntityBulkDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = (builder as BulkDeleteByColumnBuilder<P>).deferred(byColumn)

  fun deferred(builder: EntityDeleteTableBuilder) = (builder as DeleteTableBuilder<*>).deferred()
}
