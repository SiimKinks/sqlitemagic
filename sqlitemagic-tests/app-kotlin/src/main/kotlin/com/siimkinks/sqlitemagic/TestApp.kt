package com.siimkinks.sqlitemagic

import android.app.Application
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
      .database(SqliteMagicDatabase())
      .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
      .scheduleRxQueriesOn(Schedulers.trampoline())
      .openDefaultConnection()
  }
}