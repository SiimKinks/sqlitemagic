package com.siimkinks.sqlitemagic.coroutines

import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.RawMutationQuery
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Compile and execute this statement against a database and return the nr of inserted, updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of inserted, updated or deleted records
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, or the statement uses a column outside a subquery that it cannot read: a column of another
 *     table in UPDATE or DELETE, or any column in INSERT VALUES
 */
suspend fun MutationQuery.await() = compile().await()

/**
 * Execute this compiled statement against a database and return the nr of inserted, updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of inserted, updated or deleted records
 */
suspend fun CompiledMutation.await() = ReactiveAdapterSupport
  .connection(this)
  .withQueryDispatcher { execute() }

/**
 * Compile and execute this statement against a database and return the nr of inserted, updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of inserted, updated or deleted records
 */
suspend fun RawMutationQuery.await() = compile().await()
