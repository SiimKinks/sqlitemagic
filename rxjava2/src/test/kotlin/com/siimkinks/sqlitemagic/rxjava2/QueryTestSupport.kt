package com.siimkinks.sqlitemagic.rxjava2

import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

internal fun querySchedulerSettings(scheduler: Scheduler = Schedulers.trampoline()) =
  MutableScatterMap<AdapterSettingKey<*>, Any>().apply {
    this[QUERY_SCHEDULER] = scheduler
  }
