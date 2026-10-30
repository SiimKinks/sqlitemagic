package com.siimkinks.sqlitemagic.coroutines

import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.RawMutationQuery
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Compile and execute this statement against a database and return the nr of updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of updated or deleted records
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, or the WHERE clause uses a column of another table outside a subquery
 */
suspend fun MutationQuery.await() = compile().await()

/**
 * Execute this compiled statement against a database and return the nr of updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of updated or deleted records
 */
suspend fun CompiledMutation.await() = ReactiveAdapterSupport
  .connection(this)
  .withQueryDispatcher { execute() }

/**
 * Compile and execute this statement against a database and return the nr of updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of updated or deleted records
 */
suspend fun RawMutationQuery.await() = compile().await()
