package com.siimkinks.sqlitemagic.rxjava2

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

internal val QUERY_SCHEDULER = AdapterSettingKey<Scheduler>()

/**
 * Define the scheduler where RxJava handled queries will emit items.
 *
 * Defaults to [io()][Schedulers.io] scheduler.
 *
 * @param scheduler The [Scheduler] on which items are emitted when SELECT statement `observe()` methods are used
 * @return Database connection configuration builder
 */
@CheckResult
fun DatabaseSetupBuilder.scheduleRxQueriesOn(scheduler: Scheduler) = ReactiveAdapterSupport.adapterSetting(
  builder = this,
  key = QUERY_SCHEDULER,
  value = scheduler
)

internal fun DbConnection.queryScheduler() = ReactiveAdapterSupport.adapterSetting(
  connection = this,
  key = QUERY_SCHEDULER
) ?: Schedulers.io()
