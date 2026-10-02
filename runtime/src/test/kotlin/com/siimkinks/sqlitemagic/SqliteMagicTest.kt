package com.siimkinks.sqlitemagic

import android.app.Application
import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.reactivex.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class SqliteMagicTest {
  private val previousDatabase = SqliteMagic.database
  private val previousConnection = SqliteMagic.defaultConnection
  private val previousLogger = SqliteMagic.logger
  private val previousLogging = SqliteMagic.loggingEnabled
  private lateinit var androidLog: MockedStatic<Log>
  private val connections = mutableListOf<DbConnection>()

  @Before
  fun resetSingleton() {
    androidLog = mockStatic(Log::class.java)
    SqliteMagic.database = null
    SqliteMagic.defaultConnection = null
    SqliteMagic.logger = null
    SqliteMagic.loggingEnabled = false
  }

  @After
  fun restoreSingleton() {
    try {
      connections.forEach(DbConnection::close)
    } finally {
      SqliteMagic.database = previousDatabase
      SqliteMagic.defaultConnection = previousConnection
      SqliteMagic.logger = previousLogger
      SqliteMagic.loggingEnabled = previousLogging
      androidLog.close()
    }
  }

  @Test
  fun `missing configuration fails before initialization wrapping`() {
    val fixture = BootstrapFixture()
    val cases = mapOf(
      "SQLite Factory cannot be missing" to SqliteMagic.builder(fixture.context),
      "Generated database cannot be missing" to SqliteMagic.builder(fixture.context)
        .sqliteFactory(fixture.factory)
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
    verify(fixture.factory, never()).create(any())
    assertThat(SqliteMagic.database).isNull()
  }

  @Test
  fun `configuration uses name override or generated name including in memory database`() {
    val cases = listOf(
      NameCase(
        label = "override",
        name = "override.db",
        generatedName = "generated.db",
        expected = "override.db"
      ),
      NameCase(
        label = "unset",
        name = null,
        generatedName = "generated.db",
        expected = "generated.db"
      ),
      NameCase(
        label = "empty",
        name = "",
        generatedName = "generated.db",
        expected = "generated.db"
      ),
      NameCase(
        label = "in memory",
        name = null,
        generatedName = null,
        expected = null
      )
    )
    for (case in cases) {
      val generated = mockGeneratedDatabase()
      whenever(generated.dbName)
        .thenReturn(case.generatedName)
      whenever(generated.dbVersion)
        .thenReturn(7)
      val fixture = BootstrapFixture(generated)

      connections += fixture.builder
        .name(case.name)
        .openNewConnection()

      assertWithMessage(case.label)
        .that(fixture.configurations.single().name)
        .isEqualTo(case.expected)
      assertWithMessage(case.label)
        .that(fixture.configurations.single().callback.version)
        .isEqualTo(7)
      assertWithMessage(case.label)
        .that(fixture.configurations.single().context)
        .isSameInstanceAs(fixture.context)
    }
  }

  @Test
  fun `query scheduler uses io by default and accepts a configured scheduler`() {
    for ((label, scheduler) in mapOf("default" to null, "custom" to Schedulers.trampoline())) {
      val fixture = BootstrapFixture()
      scheduler?.let(fixture.builder::scheduleRxQueriesOn)

      val connection = fixture.open()

      assertWithMessage(label)
        .that(connection.queryScheduler)
        .isSameInstanceAs(scheduler ?: Schedulers.io())
    }
  }

  @Test
  fun `configured downgrade callback receives database and versions`() {
    val fixture = BootstrapFixture()
    val downgrader = mock<DbDowngrader>()
    fixture.builder.downgrader(downgrader)
    fixture.open()

    fixture.configurations
      .single()
      .callback
      .onDowngrade(fixture.db, 7, 2)

    verify(downgrader).onDowngrade(
      db = fixture.db,
      oldVersion = 7,
      newVersion = 2
    )
  }

  @Test
  fun `default downgrade callback recreates the configured database schema`() {
    val generated = mockGeneratedDatabase()
    val fixture = BootstrapFixture(generated)
    val cursor = mock<Cursor>()
    whenever(fixture.db.query(any<String>()))
      .thenReturn(cursor)
    fixture.open()

    fixture.configurations
      .single()
      .callback
      .onDowngrade(fixture.db, 7, 2)

    verify(cursor).close()
    verify(generated).createSchema(fixture.db)
  }

  @Test
  fun `helper creation and connection initialization failures retain their original cause`() {
    for (label in listOf("factory", "connection")) {
      val failure = IllegalArgumentException(label)
      val generated = mockGeneratedDatabase()
      val fixture = BootstrapFixture(generated)
      when (label) {
        "factory" -> whenever(fixture.factory.create(any()))
          .thenThrow(failure)
        else -> whenever(generated.getNrOfTables(any()))
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
    val accesses = mapOf(
      "public connection" to SqliteMagic::getDefaultConnection,
      "internal connection" to SqliteMagic::getDefaultDbConnection,
      "transaction" to SqliteMagic::newTransaction
    )
    for ((label, access) in accesses) {
      val failure = assertThrows(IllegalStateException::class.java) { access() }
      assertWithMessage(label)
        .that(failure)
        .hasMessageThat()
        .isEqualTo("Looks like SqliteMagic is not initialized...")
    }
    val fixture = BootstrapFixture()
    fixture.builder.openDefaultConnection()
    val connection = SqliteMagic.getDefaultDbConnection()
    connections += connection

    assertThat(SqliteMagic.getDefaultConnection()).isSameInstanceAs(connection)
    SqliteMagic.newTransaction().use(Transaction::markSuccessful)

    verify(fixture.db).beginTransactionWithListener(any())
    verify(fixture.db).setTransactionSuccessful()
    verify(fixture.db).endTransaction()
  }

  @Test
  fun `independent connections leave default open while default replacement closes before creation`() {
    val original = BootstrapFixture()
    original.builder.openDefaultConnection()
    val first = SqliteMagic.getDefaultDbConnection()
    connections += first
    val independent = BootstrapFixture()

    val second = independent.open()

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(SqliteMagic.getDefaultConnection()).isSameInstanceAs(first)
    verify(original.helper, never()).close()
    assertThat(SqliteMagic.database).isSameInstanceAs(independent.generated)

    val replacement = BootstrapFixture()
    whenever(replacement.factory.create(any()))
      .thenAnswer { invocation ->
        verify(original.helper).close()
        assertThat(SqliteMagic.defaultConnection).isSameInstanceAs(first)
        replacement.configurations += invocation.getArgument<SupportSQLiteOpenHelper.Configuration>(0)
        replacement.helper
      }

    replacement.builder.openDefaultConnection()
    val third = SqliteMagic.getDefaultDbConnection()
    connections += third

    assertThat(third).isNotSameInstanceAs(first)
    assertThat(third.writableDatabase).isSameInstanceAs(replacement.db)
    verify(independent.helper, never()).close()
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

  @Test
  fun `transformed value dispatch uses the database registered by opening a connection`() {
    val value = Any()
    val column = mock<Column<Any, Any, Any, Any, NotNullable>>()
    val generated = mockGeneratedDatabase()
    whenever(generated.columnForValue(value))
      .thenReturn(column)
    BootstrapFixture(generated).open()

    assertThat(Select.asColumn(value)).isSameInstanceAs(column)
    verify(generated).columnForValue(value)
  }

  private fun mockGeneratedDatabase() = mock<GeneratedDatabase>()
    .also { database ->
      whenever(database.dbVersion)
        .thenReturn(1)
    }

  private fun BootstrapFixture.open() = (builder.openNewConnection() as DbConnectionImpl)
    .also(connections::add)

  private class NameCase(
    val label: String,
    val name: String?,
    val generatedName: String?,
    val expected: String?
  )
}

private class BootstrapFixture(
  val generated: GeneratedDatabase = TestGeneratedDatabase(tableCount = 0)
) {
  val context = mock<Application>()
  val db = mock<SupportSQLiteDatabase>()
  val helper = mock<SupportSQLiteOpenHelper>()
  val factory = mock<SupportSQLiteOpenHelper.Factory>()
  val configurations = mutableListOf<SupportSQLiteOpenHelper.Configuration>()
  val builder = SqliteMagic.builder(context)
    .database(generated)
    .sqliteFactory(factory)

  init {
    whenever(helper.writableDatabase)
      .thenReturn(db)
    doAnswer { invocation ->
      configurations += invocation.getArgument<SupportSQLiteOpenHelper.Configuration>(0)
      helper
    }
      .whenever(factory)
      .create(any())
  }
}
