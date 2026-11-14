package com.siimkinks.sqlitemagic.rxjava3

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.CompiledRowIdInsert
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.RawMutationQuery
import com.siimkinks.sqlitemagic.RowIdInsertQuery
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

/**
 * Compile this statement and create a [Single] that when subscribed to executes this statement against a database
 * and emits nr of inserted, updated or deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, or the statement reads a column that it cannot read: a column of another table outside a
 *     subquery in UPDATE, DELETE or DO UPDATE, any column outside a subquery in INSERT VALUES, `EXCLUDED` of a
 *     column outside the inserted occurrence, or a column that a query reads from a table that neither its
 *     FROM nor an enclosing statement has
 */
@CheckResult
fun MutationQuery.observe(): Single<Int> = compile().observe()

/**
 * Create a [Single] that when subscribed to executes this compiled statement against a database and emits nr of
 * inserted, updated or deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
 */
@CheckResult
fun CompiledMutation.observe(): Single<Int> = Single.fromCallable(this::execute)

/**
 * Compile this statement and create a [Single] that when subscribed to executes this statement against a database
 * and emits nr of inserted, updated or deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
 */
@CheckResult
fun RawMutationQuery.observe(): Single<Int> = compile().observe()

/**
 * Compile this statement and create a [Maybe] that when subscribed to executes this statement against a database
 * and emits the row ID of the inserted row to downstream only once, or completes without a value when no row was
 * inserted, such as a row that `CONFLICT_IGNORE` ignored.
 *
 * @return Deferred [Maybe] that when subscribed to executes the statement and emits its result to downstream
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, the statement uses a column outside a subquery in VALUES, or a query reads a column of a
 *     table that neither its FROM nor an enclosing statement has
 */
@CheckResult
fun RowIdInsertQuery.observe(): Maybe<Long> = compile().observe()

/**
 * Create a [Maybe] that when subscribed to executes this compiled statement against a database and emits the row ID
 * of the inserted row to downstream only once, or completes without a value when no row was inserted, such as a row
 * that `CONFLICT_IGNORE` ignored.
 *
 * @return Deferred [Maybe] that when subscribed to executes the statement and emits its result to downstream
 */
@CheckResult
fun CompiledRowIdInsert.observe(): Maybe<Long> = Maybe.fromCallable(this::execute)
