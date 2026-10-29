package com.siimkinks.sqlitemagic.rxjava3

import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers

internal fun querySchedulerSettings(scheduler: Scheduler = Schedulers.trampoline()) =
  MutableScatterMap<AdapterSettingKey<*>, Any>().apply {
    this[QUERY_SCHEDULER] = scheduler
  }
