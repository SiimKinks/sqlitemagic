package com.siimkinks.sqlitemagic

import android.app.Application
import io.reactivex.schedulers.Schedulers

class TestApp : Application() {
  lateinit var INSTANCE: Application

  override fun onCreate() {
    super.onCreate()
    INSTANCE = this
    initDb(this)
  }

  fun initDb(app: Application) {
    SqliteMagic.setLoggingEnabled(true)
    SqliteMagic.setup(app)
        .scheduleRxQueriesOn(Schedulers.trampoline())
        .init()
  }
}