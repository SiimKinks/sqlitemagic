package com.siimkinks.sqlitemagic

import android.app.Application
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory
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
    SqliteMagic.builder(app)
        .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
        .scheduleRxQueriesOn(Schedulers.trampoline())
        .openDefaultConnection()
  }
}