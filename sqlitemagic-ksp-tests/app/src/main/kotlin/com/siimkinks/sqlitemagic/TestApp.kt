package com.siimkinks.sqlitemagic

import android.app.Application
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.siimkinks.sqlitemagic.BuildConfig.*
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleDatabaseConfig
import io.reactivex.schedulers.Schedulers

@Database(
  name = DB_NAME,
  version = DB_VERSION,
  submodules = [
    SubmoduleDatabaseConfig::class,
  ]
)
class TestApp : Application() {
  override fun onCreate() {
    super.onCreate()
    deleteDatabase(SqliteMagicDatabase().dbName)
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
