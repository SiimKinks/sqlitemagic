package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class ConnectionOptions(
  val readerConnections: Int = DEFAULT_READER_CONNECTIONS,
  val poolTimeout: Duration = DEFAULT_POOL_TIMEOUT,
  val configureConnection: ((SQLiteConnection) -> Unit)? = null
)

internal interface SchemaCallback {
  val version: Int

  fun applySchemaPragmas(connection: SQLiteConnection)

  fun create(connection: SQLiteConnection)

  fun upgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  )

  fun downgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  )

  fun open(connection: SQLiteConnection)
}

internal const val DEFAULT_READER_CONNECTIONS = 4
internal val DEFAULT_POOL_TIMEOUT = 30.seconds
internal const val MEMORY_DATABASE = ":memory:"
