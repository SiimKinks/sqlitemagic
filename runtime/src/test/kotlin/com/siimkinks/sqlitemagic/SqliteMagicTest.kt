package com.siimkinks.sqlitemagic

import android.app.Application
import android.util.Log
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.InitializationFailure.CONNECTION
import com.siimkinks.sqlitemagic.InitializationFailure.DATABASE_PATH
import com.siimkinks.sqlitemagic.InitializationFailure.DRIVER
import com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import com.siimkinks.sqlitemagic.connection.databasePath
import com.siimkinks.sqlitemagic.connection.long
import com.siimkinks.sqlitemagic.connection.whileHeldBy
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class SqliteMagicTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val previousConnection = SqliteMagic.defaultConnection
  private val previousLogger = SqliteMagic.logger
  private val previousLogging = SqliteMagic.loggingEnabled
  private lateinit var androidLog: MockedStatic<Log>
  private val connections = mutableListOf<DbConnection>()

  @Before
  fun resetSingleton() {
    androidLog = mockStatic(Log::class.java)
    SqliteMagic.defaultConnection = null
    SqliteMagic.logger = null
    SqliteMagic.loggingEnabled = false
  }

  @After
  fun restoreSingleton() {
    try {
      connections.forEach(DbConnection::close)
    } finally {
      SqliteMagic.defaultConnection = previousConnection
      SqliteMagic.logger = previousLogger
      SqliteMagic.loggingEnabled = previousLogging
      androidLog.close()
    }
  }

  @Test
  fun `missing configuration fails before initialization wrapping`() {
    val fixture = BootstrapFixture(folder.root)
    val cases = mapOf(
      "SQLite driver cannot be missing" to SqliteMagic.builder(fixture.context),
      "Generated database cannot be missing" to SqliteMagic.builder(fixture.context)
        .driver(fixture.driver)
    )
    for ((message, builder) in cases) {
      val failure = assertThrows(
        NullPointerException::class.java,
        builder::openNewConnection
      )

      assertWithMessage(message)
        .that(failure)
        .hasMessageThat()
        .isEqualTo(message)
      assertThat(failure.cause).isNull()
    }
    assertThat(fixture.opened).isEmpty()
  }

  @Test
  fun `configuration uses name override or generated name including in memory database`() {
    class NameCase(
      val label: String,
      val name: String?,
      val generatedName: String?,
      val expected: String
    )

    val cases = listOf(
      NameCase(
        label = "override",
        name = "override.db",
        generatedName = "generated.db",
        expected = folder.databasePath("override.db")
      ),
      NameCase(
        label = "unset",
        name = null,
        generatedName = "generated.db",
        expected = folder.databasePath("generated.db")
      ),
      NameCase(
        label = "empty",
        name = "",
        generatedName = "generated.db",
        expected = folder.databasePath("generated.db")
      ),
      NameCase(
        label = "in memory",
        name = null,
        generatedName = null,
        expected = MEMORY_DATABASE
      )
    )
    for (case in cases) {
      val generated = mockGeneratedDatabase()
      whenever(generated.dbName)
        .thenReturn(case.generatedName)
      whenever(generated.dbVersion)
        .thenReturn(7)
      val fixture = BootstrapFixture(
        databaseDirectory = folder.root,
        generated = generated
      )

      val version = fixture.builder
        .name(case.name)
        .open()
        .userVersion()

      assertWithMessage(case.label)
        .that(fixture.opened to version)
        .isEqualTo(listOf(case.expected) to 7L)
    }
  }

  @Test
  fun `each connection keeps the adapter settings it was opened with`() {
    val first = AdapterSettingKey<String>()
    val second = AdapterSettingKey<String>()
    val fixture = BootstrapFixture(folder.root)
    val opened = fixture.builder
      .adapterSetting(
        key = first,
        value = "first"
      )
      .open()
    val reopened = fixture.builder
      .adapterSetting(
        key = first,
        value = "changed"
      )
      .adapterSetting(
        key = second,
        value = "second"
      )
      .open()

    assertThat(
      listOf(opened, reopened).map { connection ->
        listOf(connection.adapterSetting(first), connection.adapterSetting(second))
      }
    ).isEqualTo(
      listOf(
        listOf("first", null),
        listOf("changed", "second")
      )
    )
  }

  @Test
  fun `pool options and the connection hook configure the opened connections`() {
    val connection = BootstrapFixture(folder.root).builder
      .readerConnections(0)
      .connectionPoolTimeout(100.milliseconds)
      .configureConnection { it.execSQL("PRAGMA cache_size = -1234") }
      .open()
    val cacheSize = connection.write { pooled -> pooled.connection.long("PRAGMA cache_size") }
    val timeout = whileHeldBy(
      name = "holder",
      hold = { release -> connection.write { release() } },
      attempt = { sqlExceptionMessage { connection.write {} } }
    )

    assertThat(
      Triple(
        first = connection.pool.maxReaders,
        second = cacheSize,
        third = timeout
      )
    ).isEqualTo(
      Triple(
        first = 0,
        second = -1234L,
        third = "Timed out after 100ms waiting for the writer connection, held by thread holder"
      )
    )
  }

  @Test
  fun `pool options reject negative reader counts and non-positive timeouts`() {
    val builder = BootstrapFixture(folder.root).builder
    val failures = listOf(
      assertThrows(IllegalArgumentException::class.java) { builder.readerConnections(-1) },
      assertThrows(IllegalArgumentException::class.java) { builder.connectionPoolTimeout(Duration.ZERO) }
    )

    assertThat(failures.map(Throwable::message))
      .containsExactly(
        "Reader connection count cannot be negative: -1",
        "Connection pool timeout must be positive: 0s"
      )
      .inOrder()
  }

  @Test
  fun `configured downgrade callback receives the connection and versions`() {
    BundledSQLiteDriver()
      .open(folder.databasePath())
      .use { it.execSQL("PRAGMA user_version = 7") }
    val fixture = BootstrapFixture(folder.root)
    val downgrader = mock<DbDowngrader>()

    val version = fixture.builder
      .downgrader(downgrader)
      .open()
      .userVersion()

    verify(downgrader).onDowngrade(
      connection = any(),
      oldVersion = eq(7),
      newVersion = eq(1)
    )
    assertThat(version).isEqualTo(1L)
  }

  @Test
  fun `default downgrade callback recreates the configured database schema`() {
    BundledSQLiteDriver()
      .open(folder.databasePath())
      .use { connection ->
        connection.execSQL("CREATE TABLE newer_item (id INTEGER)")
        connection.execSQL("PRAGMA user_version = 7")
      }
    val fixture = BootstrapFixture(
      databaseDirectory = folder.root,
      generated = TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf("CREATE TABLE item (id INTEGER)")
      )
    )

    val tables = fixture.builder
      .open()
      .write { pooled ->
        pooled.connection.query("SELECT name FROM sqlite_master WHERE type = 'table'") { statement ->
          statement.getText(0)
        }
      }

    assertThat(tables).containsExactly("item")
  }

  @Test
  fun `driver, database path and connection initialization failures retain their original cause`() {
    InitializationFailure.entries.forEach { stage ->
      val label = stage.name
      val failure = IllegalArgumentException(label)
      val generated = mockGeneratedDatabase()
      whenever(generated.dbName)
        .thenReturn("test.db")
      val fixture = BootstrapFixture(
        databaseDirectory = folder.root,
        generated = generated,
        driverFailure = failure.takeIf { stage == DRIVER }
      )
      when (stage) {
        DRIVER -> Unit
        DATABASE_PATH -> whenever(fixture.context.getDatabasePath(any()))
          .thenThrow(failure)
        CONNECTION -> whenever(generated.getNrOfTables(any()))
          .thenThrow(failure)
      }

      val wrapped = assertThrows(
        IllegalStateException::class.java,
        fixture.builder::openNewConnection
      )

      assertWithMessage(label)
        .that(wrapped)
        .hasMessageThat()
        .isEqualTo(
          "Error initializing database. Make sure there is at least one model annotated with @Table"
        )
      assertWithMessage(label)
        .that(wrapped.cause)
        .isSameInstanceAs(failure)
    }
  }

  @Test
  fun `default access fails before initialization and transactions use the configured connection`() {
    val accesses = mapOf<String, () -> Any>(
      "public connection" to SqliteMagic::getDefaultConnection,
      "internal connection" to SqliteMagic::defaultDbConnection,
      "transaction" to SqliteMagic::newTransaction
    )
    for ((label, access) in accesses) {
      val failure = assertThrows(IllegalStateException::class.java, access::invoke)
      assertWithMessage(label)
        .that(failure)
        .hasMessageThat()
        .isEqualTo("Looks like SqliteMagic is not initialized...")
    }
    val fixture = BootstrapFixture(folder.root)
    fixture.builder.openDefaultConnection()
    val connection = SqliteMagic.defaultDbConnection
    connections += connection

    assertThat(SqliteMagic.getDefaultConnection()).isSameInstanceAs(connection)
    val inTransaction = SqliteMagic.newTransaction()
      .use { transaction ->
        transaction.markSuccessful()
        connection.hasActiveTransaction
      }

    assertThat(inTransaction to connection.hasActiveTransaction).isEqualTo(true to false)
  }

  @Test
  fun `independent connections leave default open while default replacement closes before creation`() {
    BootstrapFixture(folder.root).builder.openDefaultConnection()
    val first = SqliteMagic.defaultDbConnection
    connections += first

    val second = BootstrapFixture(folder.root).builder.open()

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(SqliteMagic.getDefaultConnection()).isSameInstanceAs(first)
    assertThat(first.isClosed).isFalse()

    var firstClosedOnCreation: Boolean? = null
    val replacement = BootstrapFixture(
      databaseDirectory = folder.root,
      generated = object : GeneratedDatabase by TestGeneratedDatabase(tableCount = 0) {
        override fun getNrOfTables(moduleName: String?): Int {
          firstClosedOnCreation = first.isClosed
          return 0
        }
      }
    )
    replacement.builder.openDefaultConnection()
    val third = SqliteMagic.defaultDbConnection
    connections += third

    assertThat(third).isNotSameInstanceAs(first)
    assertThat(listOf(firstClosedOnCreation, first.isClosed, second.isClosed, third.isClosed))
      .isEqualTo(listOf(true, true, false, false))
  }

  @Test
  fun `logging lazily creates the default logger and preserves the configured logger`() {
    SqliteMagic.setLoggingEnabled(false)
    assertThat(SqliteMagic.logger).isNull()

    SqliteMagic.setLoggingEnabled(true)
    val defaultLogger = SqliteMagic.logger
    assertThat(defaultLogger).isInstanceOf(DefaultLogger::class.java)
    SqliteMagic.setLoggingEnabled(false)
    SqliteMagic.setLoggingEnabled(true)
    assertThat(SqliteMagic.logger).isSameInstanceAs(defaultLogger)

    val custom = mock<Logger>()
    SqliteMagic.setLogger(custom)
    for (enabled in listOf(false, true)) {
      SqliteMagic.setLoggingEnabled(enabled)
      assertWithMessage("enabled=$enabled")
        .that(SqliteMagic.loggingEnabled)
        .isEqualTo(enabled)
      assertWithMessage("enabled=$enabled")
        .that(SqliteMagic.logger)
        .isSameInstanceAs(custom)
    }
  }

  private fun mockGeneratedDatabase() = mock<GeneratedDatabase> {
    on { dbVersion } doReturn 1
  }

  private fun DatabaseSetupBuilder.open() = (openNewConnection() as DbConnectionImpl)
    .also(connections::add)

  private fun DbConnectionImpl.userVersion() = write { pooled -> pooled.connection.long("PRAGMA user_version") }
}

private class BootstrapFixture(
  databaseDirectory: File,
  generated: GeneratedDatabase = TestGeneratedDatabase(tableCount = 0),
  driverFailure: RuntimeException? = null
) {
  val context = mock<Application>()
  val opened = mutableListOf<String>()
  val driver = object : SQLiteDriver {
    override val hasConnectionPool: Boolean
      get() = when (driverFailure) {
        null -> false
        else -> throw driverFailure
      }

    override fun open(fileName: String) = BundledSQLiteDriver()
      .open(fileName)
      .also { opened += fileName }
  }

  init {
    whenever(context.getDatabasePath(any()))
      .thenAnswer { File(databaseDirectory, it.getArgument(0)) }
  }

  val builder = SqliteMagic.builder(context)
    .database(generated)
    .driver(driver)
}

private enum class InitializationFailure {
  DRIVER,
  DATABASE_PATH,
  CONNECTION
}
