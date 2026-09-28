package com.siimkinks.sqlitemagic.runtime.support

import android.app.Application
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
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
  database: GeneratedDatabase = SqliteMagicDatabase(),
  sqliteFactory: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
): DbConnection = SqliteMagic
  .builder(application)
  .name(databaseName)
  .database(database)
  .sqliteFactory(sqliteFactory)
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

private val emptyDatabaseCallback = object : SupportSQLiteOpenHelper.Callback(1) {
  override fun onCreate(db: SupportSQLiteDatabase) = Unit

  override fun onUpgrade(
    db: SupportSQLiteDatabase,
    oldVersion: Int,
    newVersion: Int
  ) = Unit
}

internal fun withEmptyDatabase(
  databaseName: String,
  block: (SupportSQLiteDatabase) -> Unit
) = withNamedDatabase(databaseName = databaseName) { application ->
  FrameworkSQLiteOpenHelperFactory()
    .create(
      SupportSQLiteOpenHelper.Configuration
        .builder(application)
        .name(databaseName)
        .callback(emptyDatabaseCallback)
        .build()
    )
    .use { helper ->
      block(helper.writableDatabase)
    }
}

internal class PrefixFailingDatabase(
  private val delegate: SupportSQLiteDatabase,
  private val sqlPrefix: String,
  private val failure: RuntimeException
) : SupportSQLiteDatabase by delegate {
  override fun execSQL(sql: String) {
    if (sql.startsWith(sqlPrefix)) {
      throw failure
    }
    delegate.execSQL(sql)
  }
}

internal fun mainSchemaObjectNames(
  connection: DbConnection,
  type: String
) = Select
  .raw("SELECT name FROM sqlite_master WHERE type = ?")
  .from(ANONYMOUS_TABLE)
  .withArgs(type)
  .usingConnection(connection)
  .execute()
  .use(Cursor::readStrings)

internal fun temporarySchemaObjectNames(
  connection: DbConnection,
  type: String
) = Select
  .raw("SELECT name FROM sqlite_temp_master WHERE type = ?")
  .from(ANONYMOUS_TABLE)
  .withArgs(type)
  .usingConnection(connection)
  .execute()
  .use(Cursor::readStrings)

internal fun mainSchemaObjectNames(
  database: SupportSQLiteDatabase,
  type: String
) = database
  .query("SELECT name FROM sqlite_master WHERE type = ?", arrayOf(type))
  .use(Cursor::readStrings)

internal fun temporarySchemaObjectNames(
  database: SupportSQLiteDatabase,
  type: String
) = database
  .query("SELECT name FROM sqlite_temp_master WHERE type = ?", arrayOf(type))
  .use(Cursor::readStrings)

internal fun mainSchemaObjectNames(database: SupportSQLiteDatabase) = database
  .query("SELECT name FROM sqlite_master WHERE type IN ('table', 'view', 'index')")
  .use(Cursor::readStrings)

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
