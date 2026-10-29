package com.siimkinks.sqlitemagic

import android.app.Application
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
import androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.LogUtil.logInfo
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

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
    private var sqliteFactory: Factory? = null
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
     * Define a Factory class to create instances of [SupportSQLiteOpenHelper].
     *
     * @param factory The factory to use while creating the open helper.
     * @return Database connection configuration builder
     */
    @CheckResult
    fun sqliteFactory(factory: Factory) = apply {
      sqliteFactory = factory
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
      val sqliteFactory = sqliteFactory ?: throw NullPointerException("SQLite Factory cannot be missing")
      val database = database ?: throw NullPointerException("Generated database cannot be missing")
      val downgrader = downgrader ?: DefaultDbDowngrader(database)
      return try {
        val name = when {
          name.isNullOrEmpty() -> database.dbName
          else -> name
        }
        val version = database.dbVersion
        val dbCallback = DbCallback(
          context = context,
          version = version,
          database = database,
          downgrader = downgrader
        )
        val configuration = Configuration.builder(context)
          .name(name)
          .callback(dbCallback)
          .build()
        val helper = sqliteFactory.create(configuration)
        logInfo("Initializing database with [name=$name, version=$version, logging=$loggingEnabled]")
        DbConnectionImpl(
          database = database,
          dbHelper = helper,
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
