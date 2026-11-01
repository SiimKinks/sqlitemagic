package com.siimkinks.sqlitemagic

import android.app.Application
import androidx.annotation.CheckResult
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import com.siimkinks.sqlitemagic.connection.ConnectionOptions
import com.siimkinks.sqlitemagic.connection.DEFAULT_POOL_TIMEOUT
import com.siimkinks.sqlitemagic.connection.DEFAULT_READER_CONNECTIONS
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.LogUtil.logInfo
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import kotlin.time.Duration

object SqliteMagic {
  var loggingEnabled = false
    internal set
  internal var logger: Logger? = null
  internal var defaultConnection: DbConnectionImpl? = null
  internal val defaultDbConnection get() = defaultConnection ?: error("Looks like SqliteMagic is not initialized...")

  /**
   * Starts new transaction on the default DB connection.
   *
   * @return A new transaction on the default DB connection
   * @see DbConnection.newTransaction
   */
  @CheckResult
  fun newTransaction(): Transaction = defaultDbConnection.newTransaction()

  /** @return Default DB connection. */
  @CheckResult
  fun getDefaultConnection(): DbConnection = defaultDbConnection

  /**
   * Specify a custom logger for debug messages when [logging is enabled][setLoggingEnabled].
   *
   * @param logger Custom database actions logger
   */
  fun setLogger(logger: Logger) {
    this.logger = logger
  }

  /**
   * Create a new database connection configuration builder.
   *
   * @param context Application context
   * @return A new database connection configuration builder
   */
  @CheckResult
  fun builder(context: Application) = DatabaseSetupBuilder(context)

  /**
   * Control whether logging is enabled.
   *
   * @param enabled Is logging enabled
   */
  fun setLoggingEnabled(enabled: Boolean) {
    if (enabled && logger == null) {
      logger = DefaultLogger()
    }
    loggingEnabled = enabled
  }

  /** Database connection configuration builder. */
  class DatabaseSetupBuilder internal constructor(
    private val context: Application
  ) {
    private var database: GeneratedDatabase? = null
    private var name: String? = null
    private var driver: SQLiteDriver? = null
    private var readerConnections = DEFAULT_READER_CONNECTIONS
    private var connectionPoolTimeout = DEFAULT_POOL_TIMEOUT
    private var configureConnection: ((SQLiteConnection) -> Unit)? = null
    private var downgrader: DbDowngrader? = null
    private val adapterSettings = MutableScatterMap<AdapterSettingKey<*>, Any>(initialCapacity = 0)

    /**
     * Provide generated database.
     *
     * @param database Generated database instance
     * @return Database connection configuration builder
     */
    @CheckResult
    fun database(database: GeneratedDatabase) = apply {
      this.database = database
    }

    /**
     * Define a database name.
     *
     * If this is not defined system will search database name from
     * the Gradle configuration. Failed to find it there `null` will be used
     * which results in in-memory database usage.
     *
     * @param name Database name
     * @return Database connection configuration builder
     */
    @CheckResult
    fun name(name: String?) = apply {
      this.name = name
    }

    /**
     * Define the SQLite driver that opens the database connections.
     *
     * The driver decides the SQLite engine: for example `BundledSQLiteDriver` for a SQLite bundled with the
     * application, or `FrameworkSQLiteDriver` from the `sqlitemagic-framework-driver` artifact for the SQLite of the
     * device.
     *
     * @param driver SQLite driver
     * @return Database connection configuration builder
     */
    @CheckResult
    fun driver(driver: SQLiteDriver) = apply {
      this.driver = driver
    }

    /**
     * Define how many reader connections can read the database concurrently with the writer connection.
     *
     * Reader connections open when needed and use the WAL journal mode. With 0 reader connections one connection
     * runs every statement and the journal mode is left to [configureConnection]. In-memory databases and drivers
     * with their own connection pool always use one connection. Defaults to 4.
     *
     * @param count Number of reader connections
     * @return Database connection configuration builder
     * @throws IllegalArgumentException if [count] is negative
     */
    @CheckResult
    fun readerConnections(count: Int) = apply {
      require(count >= 0) { "Reader connection count cannot be negative: $count" }
      readerConnections = count
    }

    /**
     * Define how long a statement waits for a database connection that other threads use.
     *
     * A statement that waits longer fails with `SQLException`, which names the connection it waited for. Use
     * [Duration.INFINITE] to wait without a limit. Defaults to 30 seconds.
     *
     * @param timeout Longest wait for a connection
     * @return Database connection configuration builder
     * @throws IllegalArgumentException if [timeout] is not positive
     */
    @CheckResult
    fun connectionPoolTimeout(timeout: Duration) = apply {
      require(timeout.isPositive()) { "Connection pool timeout must be positive: $timeout" }
      connectionPoolTimeout = timeout
    }

    /**
     * Configure each database connection when it opens, for example with PRAGMA statements.
     *
     * The configuration runs on every connection before the database schema is created or upgraded. It runs after
     * the library's connection defaults, so its settings take precedence over them. The settings the library needs
     * are applied after it: `journal_mode = WAL` with reader connections, `query_only` on reader connections and
     * `foreign_keys = ON` for schemas with cascading deletes.
     *
     * Example:
     * ```
     * SqliteMagic.builder(application)
     *     .configureConnection { connection ->
     *       connection.execSQL("PRAGMA synchronous = FULL")
     *     }
     * ```
     *
     * @param configure Configuration of a connection
     * @return Database connection configuration builder
     */
    @CheckResult
    fun configureConnection(configure: (SQLiteConnection) -> Unit) = apply {
      configureConnection = configure
    }

    /**
     * Define a callback for database downgrading.
     *
     * @param downgrader Database downgrading callback.
     * @return Database connection configuration builder
     */
    @CheckResult
    fun downgrader(downgrader: DbDowngrader) = apply {
      this.downgrader = downgrader
    }

    internal fun <V : Any> adapterSetting(
      key: AdapterSettingKey<V>,
      value: V
    ) = apply {
      adapterSettings[key] = value
    }

    /**
     * Initialize library.
     *
     * This will create the default DB connection. The database is opened on first access, which
     * creates tables on the first initialization and runs any upgrade scripts if needed.
     *
     * Call this once during application creation, before accessing the database. The default
     * connection remains open and unchanged for the application lifetime and must not be closed
     * or replaced during normal application operation.
     */
    fun openDefaultConnection() {
      defaultConnection?.close()
      defaultConnection = openConnection()
    }

    /**
     * Open a new database connection.
     *
     * The database is opened on first access, which creates a new database if needed, creates
     * tables on the first initialization and runs any upgrade scripts if needed.
     *
     * @return New database connection
     */
    @CheckResult
    fun openNewConnection(): DbConnection = openConnection()

    private fun openConnection(): DbConnectionImpl {
      val driver = driver ?: throw NullPointerException("SQLite driver cannot be missing")
      val database = database ?: throw NullPointerException("Generated database cannot be missing")
      val downgrader = downgrader ?: DefaultDbDowngrader(database)
      return try {
        val name = when {
          name.isNullOrEmpty() -> database.dbName
          else -> name
        }
        logInfo("Initializing database with [name=$name, version=${database.dbVersion}, logging=$loggingEnabled]")
        DbConnectionImpl(
          database = database,
          schema = DatabaseSchema(
            resources = { context.assets.open(it.name) },
            database = database,
            downgrader = downgrader
          ),
          driver = driver,
          fileName = when (name) {
            null -> MEMORY_DATABASE
            else -> context
              .getDatabasePath(name)
              .path
          },
          options = ConnectionOptions(
            readerConnections = readerConnections,
            poolTimeout = connectionPoolTimeout,
            configureConnection = configureConnection
          ),
          adapterSettings = MutableScatterMap<AdapterSettingKey<*>, Any>(adapterSettings.size).apply {
            adapterSettings.forEach(::set)
          }
        )
      } catch (exception: Exception) {
        throw IllegalStateException(
          "Error initializing database. Make sure there is at least one model annotated with @Table",
          exception
        )
      }
    }
  }
}
