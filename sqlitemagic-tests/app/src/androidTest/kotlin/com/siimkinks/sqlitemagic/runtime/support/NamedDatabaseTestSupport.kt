package com.siimkinks.sqlitemagic.runtime.support

import android.app.Application
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.TestApp
import com.siimkinks.sqlitemagic.coroutines.dispatchCoroutineQueriesOn
import com.siimkinks.sqlitemagic.rxjava2.scheduleRxQueriesOn
import com.siimkinks.sqlitemagic.rxjava3.scheduleRxQueriesOn
import com.siimkinks.sqlitemagic.testDriver
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import java.io.File
import io.reactivex.rxjava3.schedulers.Schedulers as RxJava3Schedulers

internal inline fun <T> DbConnection.withWriter(block: (SQLiteConnection) -> T) =
  (this as DbConnectionImpl).write { block(it.connection) }

internal inline fun <T> withDefaultWriter(block: (SQLiteConnection) -> T) = SqliteMagic
  .getDefaultConnection()
  .withWriter(block)

internal fun testApplication() = InstrumentationRegistry
  .getInstrumentation()
  .targetContext
  .applicationContext as TestApp

internal fun reopenDefaultConnection() = testApplication().initDb()

internal fun openNamedConnection(
  application: Application,
  databaseName: String,
  database: GeneratedDatabase = SqliteMagicDatabase()
) = SqliteMagic
  .builder(application)
  .name(databaseName)
  .database(database)
  .driver(testDriver())
  .scheduleRxQueriesOn(Schedulers.trampoline())
  .scheduleRxQueriesOn(RxJava3Schedulers.trampoline())
  .dispatchCoroutineQueriesOn(Dispatchers.Unconfined)
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

internal fun withEmptyDatabase(
  databaseName: String,
  block: (SQLiteConnection) -> Unit
) = withNamedDatabase(databaseName = databaseName) {
  openDatabaseFile(
    application = it,
    databaseName = databaseName
  ).use(block)
}

internal fun openDatabaseFile(
  application: Application,
  databaseName: String
) = testDriver().open(application.getDatabasePath(databaseName).path)

internal fun seedDatabaseFile(
  application: Application,
  databaseName: String,
  version: Int,
  statements: List<String>
) = openDatabaseFile(
  application = application,
  databaseName = databaseName
).use { connection ->
  statements.forEach(connection::execSQL)
  connection.execSQL("PRAGMA user_version = $version")
}

internal fun freshDatabasePath(name: String): String = InstrumentationRegistry
  .getInstrumentation()
  .targetContext
  .getDatabasePath(name)
  .also { file ->
    file.parentFile?.mkdirs()
    listOf("", "-journal", "-wal", "-shm").forEach { File("${file.path}$it").delete() }
  }
  .path

internal class PrefixFailingConnection(
  private val delegate: SQLiteConnection,
  private val sqlPrefix: String,
  private val failure: RuntimeException
) : SQLiteConnection by delegate {
  override fun prepare(sql: String) = when {
    sql.startsWith(sqlPrefix) -> throw failure
    else -> delegate.prepare(sql)
  }
}

internal const val SQLITE_MASTER = "sqlite_master"
internal const val SQLITE_TEMP_MASTER = "sqlite_temp_master"

internal fun schemaObjectNames(
  master: String,
  type: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = connection.withWriter {
  it.schemaObjectNames(
    master = master,
    type = type
  )
}

internal fun SQLiteConnection.schemaObjectNames(
  master: String,
  type: String
) = readStrings("SELECT name FROM $master WHERE type = ?", type)

internal fun schemaObjectSql(
  master: String,
  type: String,
  name: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = connection.withWriter {
  it
    .readStrings("SELECT sql FROM $master WHERE type = ? AND name = ?", type, name)
    .singleOrNull()
}

internal fun SQLiteConnection.long(sql: String) = prepare(sql).use { statement ->
  statement.step()
  statement.getLong(0)
}

internal fun SQLiteConnection.userVersion() = long("PRAGMA user_version").toInt()

internal fun SQLiteConnection.text(sql: String) = prepare(sql).use { statement ->
  statement.step()
  statement.getText(0)
}

internal fun SQLiteConnection.readStrings(
  sql: String,
  vararg arguments: String
) = prepare(sql).use { statement ->
  arguments.forEachIndexed { index, argument ->
    statement.bindText(
      index = index + 1,
      value = argument
    )
  }
  buildList {
    while (statement.step()) {
      add(statement.textOrNull(0))
    }
  }
}

internal fun queryRows(
  sql: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = connection.withWriter { it.readRows(sql) }

internal fun SQLiteConnection.readRows(sql: String) = prepare(sql).use { statement ->
  buildList {
    while (statement.step()) {
      add(
        List(
          size = statement.getColumnCount(),
          init = statement::textOrNull
        )
      )
    }
  }
}

private fun SQLiteStatement.textOrNull(index: Int) = when {
  isNull(index) -> null
  else -> getText(index)
}
