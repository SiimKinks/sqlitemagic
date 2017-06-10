package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation;

import java.util.Collection;

import static com.siimkinks.sqlitemagic.CompiledSelectImpl.createQueryObservable;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Builder for raw SQL SELECT statement.
 */
public final class RawSelect {
  @NonNull
  final String sql;

  RawSelect(@NonNull String sql) {
    this.sql = sql;
  }

  /**
   * Define tables that are involved in this SQL SELECT statement.
   * <p>
   * These tables are used in rx operations where subscribers will receive
   * an immediate notification for initial data as well as subsequent
   * notifications for when the supplied {@code table}'s data changes.
   *
   * @param tables
   *     Tables to select from. This param must be one of annotation processor
   *     generated table objects that corresponds to table in a database
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  public From from(@NonNull @Size(min = 1) Table<?>... tables) {
    final int len = tables.length;
    final String[] observedTables = new String[len];
    for (int i = 0; i < len; i++) {
      observedTables[i] = tables[i].nameInQuery;
    }
    return new From(this, observedTables);
  }

  /**
   * Define tables that are involved in this SQL SELECT statement.
   * <p>
   * These tables are used in rx operations where subscribers will receive
   * an immediate notification for initial data as well as subsequent
   * notifications for when the supplied {@code table}'s data changes.
   *
   * @param tables
   *     Tables to select from. This param must be one of annotation processor
   *     generated table objects that corresponds to table in a database
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  public From from(@NonNull @Size(min = 1) Collection<Table<?>> tables) {
    final int len = tables.size();
    final String[] observedTables = new String[len];
    int i = 0;
    for (Table<?> table : tables) {
      observedTables[i] = table.nameInQuery;
      i++;
    }
    return new From(this, observedTables);
  }

  /**
   * Builder for raw SQL SELECT statement.
   */
  public static final class From implements ConnectionProvidedOperation<From> {
    @NonNull
    final RawSelect select;
    @NonNull
    final String[] observedTables;
    @Nullable
    String[] args;
    @NonNull
    DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

    From(@NonNull RawSelect select, @NonNull String[] observedTables) {
      this.select = select;
      this.observedTables = observedTables;
    }

    @NonNull
    @Override
    public From usingConnection(@NonNull DbConnection connection) {
      dbConnection = (DbConnectionImpl) connection;
      return this;
    }

    /**
     * Define SQL arguments.
     *
     * @param args
     *     Arguments for the created SQL
     * @return A builder for raw SQL SELECT statement
     */
    @CheckResult
    public From withArgs(@NonNull @Size(min = 1) String... args) {
      this.args = args;
      return this;
    }

    /**
     * Compiles this SQL SELECT statement.
     * <p>
     * Result object is immutable and can be shared between threads without any side effects.
     * <p>
     * Note: This method does not compile underlying SQL statement.
     *
     * @return Immutable compiled raw SQL SELECT statement
     */
    @CheckResult
    public CompiledRawSelect compile() {
      return new CompiledRawSelectImpl(this, dbConnection);
    }

    /**
     * Compile and execute this raw SELECT statement against a database.
     * <p>
     * This method runs synchronously in the calling thread.
     *
     * @return {@link Cursor} over the result set
     */
    @NonNull
    @CheckResult
    @WorkerThread
    public Cursor execute() {
      return new CompiledRawSelectImpl(this, dbConnection).execute();
    }

    /**
     * Create an observable which will notify subscribers with a {@linkplain Query query} for
     * execution.
     * <p>
     * Subscribers will receive an immediate notification for initial data as well as subsequent
     * notifications for when the supplied {@code table}'s data changes through the SqliteMagic
     * provided model operations. Unsubscribe when you no longer want updates to a query.
     * <p>
     * Since database triggers are inherently asynchronous, items emitted from the returned
     * observable use the {@link io.reactivex.Scheduler} supplied to
     * {@link com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder#scheduleRxQueriesOn}. For
     * consistency, the immediate notification sent on subscribe also uses this scheduler. As such,
     * calling {@link io.reactivex.Observable#subscribeOn subscribeOn} on the returned observable has no
     * effect.
     * <p>
     * Note: To skip the immediate notification and only receive subsequent notifications when data
     * has changed call {@code skip(1)} on the returned observable.
     * <p>
     * One might want to explore the returned type methods for convenience query related
     * operators.
     * <p>
     * <b>Warning:</b> this method does not perform the query! Only by subscribing to the returned
     * {@link io.reactivex.Observable} will the operation occur.
     */
    @NonNull
    @CheckResult
    public SingleItemQueryObservable<Cursor> observe() {
      return new CompiledRawSelectImpl(this, dbConnection).observe();
    }
  }

  static final class CompiledRawSelectImpl extends Query.DatabaseQuery<Cursor, Cursor> implements CompiledRawSelect {
    @NonNull
    final String sql;
    @NonNull
    final String[] observedTables;
    @Nullable
    final String[] args;

    CompiledRawSelectImpl(@NonNull From from,
                          @NonNull DbConnectionImpl dbConnection) {
      super(dbConnection, null);
      this.sql = from.select.sql;
      this.observedTables = from.observedTables;
      this.args = from.args;
    }

    @NonNull
    @Override
    SqliteMagicCursor rawQuery(boolean inStream) {
      super.rawQuery(inStream);
      final SQLiteDatabase db = dbConnection.getReadableDatabase();
      final long startNanos = nanoTime();
      final SqliteMagicCursor cursor = (SqliteMagicCursor) db.rawQueryWithFactory(null, sql, args, null, null);
      if (SqliteMagic.LOGGING_ENABLED) {
        final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
      }
      return cursor;
    }

    @Override
    Cursor map(@NonNull SqliteMagicCursor cursor) {
      return cursor;
    }

    @NonNull
    @Override
    public Cursor execute() {
      return rawQuery(false);
    }

    @NonNull
    @Override
    public SingleItemQueryObservable<Cursor> observe() {
      return new SingleItemQueryObservable<>(createQueryObservable(observedTables,this));
    }

    @Override
    public String toString() {
      return "[RAW; sql=" + sql + "]";
    }
  }
}
