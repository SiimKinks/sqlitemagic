package com.siimkinks.sqlitemagic.runtime.contract.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.connection.SchemaCallback

internal object AccountSchema : SchemaCallback {
  override val version = 1

  override fun applySchemaPragmas(connection: SQLiteConnection) = Unit

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
