package com.siimkinks.sqlitemagic

import android.app.Application
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.siimkinks.sqlitemagic.BuildConfig.*
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.coroutines.dispatchCoroutineQueriesOn
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.ExternalTransformer
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleDatabaseConfig
import com.siimkinks.sqlitemagic.rxjava2.scheduleRxQueriesOn
import com.siimkinks.sqlitemagic.rxjava3.scheduleRxQueriesOn
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import io.reactivex.rxjava3.schedulers.Schedulers as RxJava3Schedulers

@Database(
  name = DB_NAME,
  version = DB_VERSION,
  submodules = [
    SubmoduleDatabaseConfig::class,
  ],
  externalTransformers = [
    ExternalTransformer::class,
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
      .scheduleRxQueriesOn(RxJava3Schedulers.trampoline())
      .dispatchCoroutineQueriesOn(Dispatchers.Unconfined)
      .openDefaultConnection()
  }
}
