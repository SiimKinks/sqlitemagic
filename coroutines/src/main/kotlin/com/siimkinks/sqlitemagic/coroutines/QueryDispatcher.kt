package com.siimkinks.sqlitemagic.coroutines

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val QUERY_DISPATCHER = AdapterSettingKey<CoroutineDispatcher>()

/**
 * Define the dispatcher where coroutine database calls run: the `runQuery` operators, `runQueryOnce`,
 * `Query.run()` and every `await()`.
 *
 * Defaults to [Dispatchers.IO].
 *
 * Transactions belong to the thread that started them, so a call that this dispatcher runs on another thread does
 * not take part in a transaction of the calling thread.
 *
 * @param dispatcher The [CoroutineDispatcher] that runs the database work of coroutine calls
 * @return Database connection configuration builder
 */
@CheckResult
fun DatabaseSetupBuilder.dispatchCoroutineQueriesOn(dispatcher: CoroutineDispatcher) =
  ReactiveAdapterSupport.adapterSetting(
    builder = this,
    key = QUERY_DISPATCHER,
    value = dispatcher
  )

internal fun DbConnection.queryDispatcher() = ReactiveAdapterSupport.adapterSetting(
  connection = this,
  key = QUERY_DISPATCHER
) ?: Dispatchers.IO

internal suspend fun <T> DbConnection.withQueryDispatcher(block: suspend CoroutineScope.() -> T) = withContext(
  context = queryDispatcher(),
  block = block
)
