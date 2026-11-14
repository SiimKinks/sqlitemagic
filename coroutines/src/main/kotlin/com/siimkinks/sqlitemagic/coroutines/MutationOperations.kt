package com.siimkinks.sqlitemagic.coroutines

import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.CompiledRowIdInsert
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.RawMutationQuery
import com.siimkinks.sqlitemagic.RowIdInsertQuery
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Compile and execute this statement against a database and return the nr of inserted, updated or deleted records.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Nr of inserted, updated or deleted records
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, or the statement reads a column that it cannot read: a column of another table outside a
 *     subquery in UPDATE, DELETE or DO UPDATE, any column outside a subquery in INSERT VALUES, `EXCLUDED` of a
 *     column outside the inserted occurrence, or a column that a query reads from a table that neither its
 *     FROM nor an enclosing statement has
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

/**
 * Compile and execute this statement against a database and return the row ID of the inserted row.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Row ID of the inserted row, or `null` when no row was inserted, such as a row that `CONFLICT_IGNORE`
 *     ignored
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, the statement uses a column outside a subquery in VALUES, or a query reads a column of a
 *     table that neither its FROM nor an enclosing statement has
 */
suspend fun RowIdInsertQuery.await() = compile().await()

/**
 * Execute this compiled statement against a database and return the row ID of the inserted row.
 *
 * The statement runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Row ID of the inserted row, or `null` when no row was inserted, such as a row that `CONFLICT_IGNORE`
 *     ignored
 */
suspend fun CompiledRowIdInsert.await() = ReactiveAdapterSupport
  .connection(this)
  .withQueryDispatcher { execute() }
