package com.siimkinks.sqlitemagic.rxjava2

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.RawMutationQuery
import io.reactivex.Single

/**
 * Compile this statement and create a [Single] that when subscribed to executes this statement against a database
 * and emits nr of updated or deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
 * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
 *     parameter, or the WHERE clause uses a column of another table outside a subquery
 */
@CheckResult
fun MutationQuery.observe(): Single<Int> = compile().observe()

/**
 * Create a [Single] that when subscribed to executes this compiled statement against a database and emits nr of
 * updated or deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
 */
@CheckResult
fun CompiledMutation.observe(): Single<Int> = Single.fromCallable(::execute)

/**
 * Compile this statement and create a [Single] that when subscribed to executes this statement against a database
 * and emits nr of updated or deleted records to downstream only once.
 *
 * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
 */
@CheckResult
fun RawMutationQuery.observe(): Single<Int> = compile().observe()
