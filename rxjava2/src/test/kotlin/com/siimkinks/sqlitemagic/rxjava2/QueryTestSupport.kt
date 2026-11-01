package com.siimkinks.sqlitemagic.rxjava2

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.QueryingDriver
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.testDbConnection
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

internal fun querySchedulerSettings(scheduler: Scheduler = Schedulers.trampoline()) =
  MutableScatterMap<AdapterSettingKey<*>, Any>().apply {
    this[QUERY_SCHEDULER] = scheduler
  }

internal fun queryingConnection(
  answers: (String) -> Boolean,
  onQuery: () -> SQLiteStatement
) = testDbConnection(
  driver = QueryingDriver(
    answers = answers,
    onQuery = onQuery
  ),
  adapterSettings = querySchedulerSettings()
)
