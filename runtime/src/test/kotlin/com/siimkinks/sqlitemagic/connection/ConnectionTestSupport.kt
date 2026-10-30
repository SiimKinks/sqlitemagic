package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import kotlin.time.Duration

internal class TrackingDriver(
  private val delegate: SQLiteDriver = BundledSQLiteDriver(),
  override val hasConnectionPool: Boolean = false
) : SQLiteDriver {
  val connections: MutableList<TrackingConnection> = Collections.synchronizedList(mutableListOf())

  override fun open(fileName: String) = TrackingConnection(delegate.open(fileName)).also(connections::add)
}

internal class TrackingConnection(private val delegate: SQLiteConnection) : SQLiteConnection by delegate {
  val prepared = mutableListOf<String>()
  val statements = mutableListOf<TrackingStatement>()
  var closed = false
    private set

  override fun prepare(sql: String) = TrackingStatement(delegate.prepare(sql)).also {
    prepared += sql
    statements += it
  }

  override fun close() {
    closed = true
    delegate.close()
  }
}

internal class TrackingStatement(private val delegate: SQLiteStatement) : SQLiteStatement by delegate {
  var closed = false
    private set

  override fun close() {
    closed = true
    delegate.close()
  }
}

internal class RecordingSchema(
  override val version: Int = 1,
  private val onUpgrade: (SQLiteConnection) -> Unit = {}
) : SchemaCallback {
  val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

  override fun configure(connection: SQLiteConnection) {
    events += "configure"
  }

  override fun create(connection: SQLiteConnection) {
    events += "create"
    connection.execSQL("CREATE TABLE account(id INTEGER PRIMARY KEY, balance INTEGER NOT NULL)")
    connection.execSQL("INSERT INTO account(id, balance) VALUES (1, 100), (2, 100)")
  }

  override fun upgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  ) {
    events += "upgrade $oldVersion->$newVersion"
    onUpgrade(connection)
  }

  override fun downgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  ) {
    events += "downgrade $oldVersion->$newVersion"
  }

  override fun open(connection: SQLiteConnection) {
    events += "open"
  }
}

internal fun TemporaryFolder.databasePath(name: String = "test.db") = File(root, name).path

internal fun pool(
  fileName: String,
  driver: SQLiteDriver = TrackingDriver(),
  schema: SchemaCallback = RecordingSchema(),
  readerConnections: Int = DEFAULT_READER_CONNECTIONS,
  poolTimeout: Duration = DEFAULT_POOL_TIMEOUT,
  configureConnection: ((SQLiteConnection) -> Unit)? = null
) = ConnectionPool(
  driver = driver,
  fileName = fileName,
  options = ConnectionOptions(
    readerConnections = readerConnections,
    poolTimeout = poolTimeout,
    configureConnection = configureConnection
  ),
  schema = schema
)

internal fun SQLiteConnection.long(sql: String) = prepare(sql).use { statement ->
  statement.step()
  statement.getLong(0)
}

internal fun SQLiteConnection.text(sql: String) = prepare(sql).use { statement ->
  statement.step()
  statement.getText(0)
}

internal fun ConnectionPool.balance(id: Long = 1L) = read(requiresWriter = false) { pooled ->
  pooled.connection.long("SELECT balance FROM account WHERE id = $id")
}
