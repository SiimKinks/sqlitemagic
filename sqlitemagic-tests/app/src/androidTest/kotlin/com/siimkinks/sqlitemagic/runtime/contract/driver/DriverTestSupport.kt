package com.siimkinks.sqlitemagic.runtime.contract.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import com.siimkinks.sqlitemagic.connection.SchemaCallback
import java.io.File

internal fun freshDatabasePath(name: String): String {
  val file = InstrumentationRegistry
    .getInstrumentation()
    .targetContext
    .getDatabasePath(name)
  file.parentFile?.mkdirs()
  listOf("", "-journal", "-wal", "-shm").forEach { suffix -> File(file.path + suffix).delete() }
  return file.path
}

internal fun SQLiteConnection.long(sql: String) = prepare(sql).use { statement ->
  statement.step()
  statement.getLong(0)
}

internal fun SQLiteConnection.text(sql: String) = prepare(sql).use { statement ->
  statement.step()
  statement.getText(0)
}

internal object AccountSchema : SchemaCallback {
  override val version = 1

  override fun configure(connection: SQLiteConnection) = Unit

  override fun create(connection: SQLiteConnection) {
    connection.execSQL("CREATE TABLE account(id INTEGER PRIMARY KEY, balance INTEGER NOT NULL)")
    connection.execSQL("INSERT INTO account(id, balance) VALUES (1, 100)")
  }

  override fun upgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  ) = Unit

  override fun downgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  ) = Unit

  override fun open(connection: SQLiteConnection) = Unit
}
