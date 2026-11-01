package com.siimkinks.sqlitemagic.sample

import android.app.Application
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.sample.BuildConfig.DB_VERSION

@Database(
  name = "todo.db",
  version = DB_VERSION
)
class SampleApp : Application() {
  override fun onCreate() {
    super.onCreate()
    SqliteMagic.builder(this)
      .database(SqliteMagicDatabase())
      .driver(BundledSQLiteDriver())
      .openDefaultConnection()
  }
}
