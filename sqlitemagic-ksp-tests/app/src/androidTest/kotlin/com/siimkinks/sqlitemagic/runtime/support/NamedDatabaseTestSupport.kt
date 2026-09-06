package com.siimkinks.sqlitemagic.runtime.support

import android.app.Application
import android.database.Cursor
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.TestApp
import io.reactivex.schedulers.Schedulers

internal fun testApplication() = InstrumentationRegistry
  .getInstrumentation()
  .targetContext
  .applicationContext as TestApp

internal fun reopenDefaultConnection() {
  val application = testApplication()
  application.initDb(app = application)
}

internal fun openNamedConnection(
  application: Application,
  databaseName: String,
  database: GeneratedDatabase = SqliteMagicDatabase()
): DbConnection = SqliteMagic
  .builder(application)
  .name(databaseName)
  .database(database)
  .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
  .scheduleRxQueriesOn(Schedulers.trampoline())
  .openNewConnection()

internal inline fun withNamedDatabase(
  databaseName: String,
  block: (Application) -> Unit
) {
  val application = testApplication()
  application.deleteDatabase(databaseName)
  try {
    block(application)
  } finally {
    application.deleteDatabase(databaseName)
  }
}

internal fun Cursor.readStrings() = buildList {
  while (moveToNext()) {
    add(getString(0))
  }
}

internal fun Cursor.readRows() = buildList {
  while (moveToNext()) {
    add(buildList {
      repeat(times = columnCount) { columnIndex ->
        add(
          when {
            isNull(columnIndex) -> null
            else -> getString(columnIndex)
          }
        )
      }
    })
  }
}
