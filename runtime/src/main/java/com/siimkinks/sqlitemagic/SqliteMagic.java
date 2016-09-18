package com.siimkinks.sqlitemagic;

import android.app.Application;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import rx.Scheduler;
import rx.schedulers.Schedulers;

import static com.siimkinks.sqlitemagic.SqlUtil.getDbName;
import static com.siimkinks.sqlitemagic.SqlUtil.getDbVersion;

public final class SqliteMagic {
	static boolean LOGGING_ENABLED = false;
	static Logger LOGGER;
	@Nullable
	Application context;
	@Nullable
	DbConnectionImpl dbConnectionImpl;

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

	static DbConnectionImpl getDefaultDbConnection() {
		return SingletonHolder.instance.dbConnectionImpl;
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
	 * Initialize library with default configuration.
	 * <p>
	 * This will create and open the default DB connection; creates tables on the first
	 * initialization; runs any upgrade scripts if needed.
	 *
	 * @param context Application context
	 */
	public static void init(@NonNull Application context) {
		init(context, new DatabaseSetupBuilder());
	}

	/**
	 * Initialize library.
	 * <p>
	 * This will create and open the default DB connection; creates tables on the first
	 * initialization; runs any upgrade scripts if needed.
	 *
	 * @param context              Application context
	 * @param databaseSetupBuilder Default DB connection configuration
	 */
	public static void init(@NonNull Application context,
	                        @NonNull DatabaseSetupBuilder databaseSetupBuilder) {
		final SqliteMagic sqliteMagic = SingletonHolder.instance;
		if (sqliteMagic.dbConnectionImpl != null) {
			sqliteMagic.dbConnectionImpl.close();
		}
		sqliteMagic.context = context;
		sqliteMagic.dbConnectionImpl = openConnection(context, databaseSetupBuilder);
	}

	/**
	 * Open a new database connection.
	 * <p>
	 * This will create a new database if needed; creates tables on the first
	 * initialization; runs any upgrade scripts if needed.
	 *
	 * @param databaseSetupBuilder Database connection configuration
	 * @return Opened database connection
	 */
	@NonNull
	@CheckResult
	public static DbConnection openNewConnection(@NonNull DatabaseSetupBuilder databaseSetupBuilder) {
		final SqliteMagic sqliteMagic = SingletonHolder.instance;
		final Application context = sqliteMagic.context;
		if (context == null) {
			throw new IllegalStateException("SqliteMagic is not initialized. Make sure you have called SqliteMagic#init");
		}
		return openConnection(context, databaseSetupBuilder);
	}

	private static DbConnectionImpl openConnection(@NonNull Application context,
	                                               @NonNull DatabaseSetupBuilder databaseSetupBuilder) {
		try {
			String name = databaseSetupBuilder.name;
			if (name == null || name.isEmpty()) {
				name = getDbName();
			}
			final int version = getDbVersion();
			final DbHelper dbHelper = new DbHelper(context, name, version);
			logStartingInfo(name, version);
			return new DbConnectionImpl(dbHelper, databaseSetupBuilder.queryScheduler);
		} catch (Exception e) {
			throw new IllegalStateException("Error initializing database. " +
					"Make sure there is at least one model annotated with @Table", e);
		}
	}

	private static void logStartingInfo(@NonNull String dbName, int dbVersion) {
		LogUtil.logInfo("Initializing database with [name=%s, version=%s, logging=%s]",
				dbName, dbVersion, LOGGING_ENABLED);
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
		@Nullable
		String name;
		@NonNull
		Scheduler queryScheduler = Schedulers.io();

		DatabaseSetupBuilder() {
		}

		/**
		 * Create a new database connection configuration builder.
		 *
		 * @return A new database connection configuration builder
		 */
		@CheckResult
		public static DatabaseSetupBuilder setupDatabase() {
			return new DatabaseSetupBuilder();
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
		public DatabaseSetupBuilder withName(@NonNull String name) {
			this.name = name;
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
		public DatabaseSetupBuilder scheduleRxQueriesOn(@NonNull Scheduler scheduler) {
			this.queryScheduler = scheduler;
			return this;
		}
	}
}
