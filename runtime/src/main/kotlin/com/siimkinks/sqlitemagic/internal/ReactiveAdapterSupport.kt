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
