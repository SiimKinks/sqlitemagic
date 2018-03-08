package com.siimkinks.sqlitemagic;

import android.app.Application;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.db.SupportSQLiteOpenHelper.Configuration;
import android.arch.persistence.db.SupportSQLiteOpenHelper.Factory;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

import static com.siimkinks.sqlitemagic.SqlUtil.getDbName;
import static com.siimkinks.sqlitemagic.SqlUtil.getDbVersion;

public final class SqliteMagic {
  static boolean LOGGING_ENABLED = false;
  static Logger LOGGER;
  @Nullable
  DbConnectionImpl defaultConnection;

  static class SingletonHolder {
    public static final SqliteMagic instance = new SqliteMagic();
  }

  SqliteMagic() {
  }

  /**
   * Starts new transaction on the default DB connection.
   *
   * @return A new transaction on the default DB connection
   * @see DbConnection#newTransaction()
   */
  @NonNull
  @CheckResult
  public static Transaction newTransaction() {
    return getDefaultDbConnection().newTransaction();
  }

  /**
   * @return Default DB connection.
   */
  @NonNull
  @CheckResult
  public static DbConnection getDefaultConnection() {
    return getDefaultDbConnection();
  }

  @NonNull
  static DbConnectionImpl getDefaultDbConnection() {
    final DbConnectionImpl defaultConnection = SingletonHolder.instance.defaultConnection;
    if (defaultConnection == null) {
      throw new IllegalStateException("Looks like SqliteMagic is not initialized...");
    }
    return defaultConnection;
  }

  /**
   * Specify a custom logger for debug messages when {@linkplain #setLoggingEnabled(boolean)
   * logging is enabled}.
   *
   * @param logger Custom database actions logger
   */
  public static void setLogger(@NonNull Logger logger) {
    LOGGER = logger;
  }

  /**
   * Create a new database connection configuration builder.
   *
   * @return A new database connection configuration builder
   */
  @NonNull
  @CheckResult
  public static DatabaseSetupBuilder builder(@NonNull Application context) {
    return new DatabaseSetupBuilder(context);
  }

  static DbConnectionImpl openConnection(@NonNull Application context,
                                         @NonNull DatabaseSetupBuilder databaseSetupBuilder) {
    final Factory sqliteFactory = databaseSetupBuilder.sqliteFactory;
    if (sqliteFactory == null) {
      throw new NullPointerException("SQLite Factory cannot be null");
    }
    try {
      String name = databaseSetupBuilder.name;
      if (name == null || name.isEmpty()) {
        name = getDbName();
      }
      final int version = getDbVersion();
      final DbCallback dbCallback = new DbCallback(context, version, databaseSetupBuilder.downgrader);
      final Configuration configuration = Configuration
          .builder(context)
          .name(name)
          .callback(dbCallback)
          .build();
      final SupportSQLiteOpenHelper helper = sqliteFactory.create(configuration);
      LogUtil.logInfo("Initializing database with [name=%s, version=%s, logging=%s]",
          name, version, LOGGING_ENABLED);
      return new DbConnectionImpl(helper, databaseSetupBuilder.queryScheduler);
    } catch (Exception e) {
      throw new IllegalStateException("Error initializing database. " +
          "Make sure there is at least one model annotated with @Table", e);
    }
  }

  /**
   * Control whether logging is enabled.
   *
   * @param enabled Is logging enabled
   */
  public static void setLoggingEnabled(boolean enabled) {
    if (enabled && LOGGER == null) {
      LOGGER = new DefaultLogger();
    }
    LOGGING_ENABLED = enabled;
  }

  /**
   * Database connection configuration builder.
   */
  public static final class DatabaseSetupBuilder {
    @NonNull
    private final Application context;
    @Nullable
    String name;
    @Nullable
    Factory sqliteFactory;
    @NonNull
    DbDowngrader downgrader = new DefaultDbDowngrader();
    @NonNull
    Scheduler queryScheduler = Schedulers.io();

    DatabaseSetupBuilder(@NonNull Application context) {
      if (context == null) {
        throw new NullPointerException("Application context cannot be null");
      }
      this.context = context;
    }

    /**
     * Define a database name.
     * <p>
     * If this is not defined system will search database name from
     * the Gradle configuration. Failed to find it there {@code null} will be used
     * which results in in-memory database usage.
     *
     * @param name Database name
     * @return Database connection configuration builder
     */
    @CheckResult
    public DatabaseSetupBuilder name(@Nullable String name) {
      this.name = name;
      return this;
    }

    /**
     * Define a Factory class to create instances of {@link SupportSQLiteOpenHelper}
     *
     * @param factory The factory to use while creating the open helper.
     * @return Database connection configuration builder
     */
    @CheckResult
    public DatabaseSetupBuilder sqliteFactory(@NonNull Factory factory) {
      this.sqliteFactory = factory;
      return this;
    }

    /**
     * Define a callback for database downgrading.
     *
     * @param downgrader Database downgrading callback.
     * @return Database connection configuration builder
     */
    @CheckResult
    public DatabaseSetupBuilder downgrader(@NonNull DbDowngrader downgrader) {
      if (downgrader == null) {
        throw new NullPointerException("Database downgrading callback cannot be null");
      }
      this.downgrader = downgrader;
      return this;
    }

    /**
     * Define the scheduler where RxJava handled queries will emit items.
     * <p>
     * Defaults to {@link Schedulers#io() io()} scheduler.
     *
     * @param scheduler The {@link Scheduler} on which items are emitted when SELECT statement
     *                  {@link CompiledSelect#observe() observe()} methods are used
     * @return Database connection configuration builder
     */
    @CheckResult
    public DatabaseSetupBuilder scheduleRxQueriesOn(@NonNull Scheduler scheduler) {
      if (scheduler == null) {
        throw new NullPointerException("Rx queries scheduler cannot be null");
      }
      this.queryScheduler = scheduler;
      return this;
    }

    /**
     * Initialize library.
     * <p>
     * This will create and open the default DB connection; creates tables on the first
     * initialization; runs any upgrade scripts if needed.
     */
    public void openDefaultConnection() {
      final SqliteMagic sqliteMagic = SingletonHolder.instance;
      if (sqliteMagic.defaultConnection != null) {
        sqliteMagic.defaultConnection.close();
      }
      sqliteMagic.defaultConnection = openConnection(context, this);
    }

    /**
     * Open a new database connection.
     * <p>
     * This will create a new database if needed; creates tables on the first
     * initialization; runs any upgrade scripts if needed.
     *
     * @return Opened database connection
     */
    @NonNull
    @CheckResult
    public DbConnection openNewConnection() {
      return openConnection(context, this);
    }
  }
}
