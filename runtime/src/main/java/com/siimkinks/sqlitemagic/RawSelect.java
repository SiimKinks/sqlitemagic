package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.database.Cursor;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import java.util.Collection;

import static com.siimkinks.sqlitemagic.CompiledSelectImpl.createQueryObservable;
import static com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_STRINGS;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Builder for raw SQL SELECT statement.
 */
public final class RawSelect extends RawSelectNode<RawSelect, CompiledRawSelect> {
  RawSelect(@NonNull String sql) {
    super(new Builder(sql));
  }

  /**
   * Define tables that are involved in this SQL SELECT statement.
   * <p>
   * These tables are used in rx operations where subscribers will receive
   * an immediate notification for initial data as well as subsequent
   * notifications for when the supplied {@code table}'s data changes.
   *
   * @param tables Tables to select from. This param must be one of annotation processor
   *               generated table objects that corresponds to table in a database
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
   * @param tables Tables to select from. This param must be one of annotation processor
   *               generated table objects that corresponds to table in a database
   * @return A builder for raw SQL SELECT statement
   */
  @CheckResult
  public <T extends Table<?>> From from(@NonNull @Size(min = 1) Collection<T> tables) {
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
  public static final class From extends RawSelectNode<From, CompiledObservableRawSelect> {
    From(@NonNull RawSelect select, @NonNull String[] observedTables) {
      super(select.rawSelectBuilder);
      rawSelectBuilder.observedTables = observedTables;
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
      return new CompiledRawSelectImpl(rawSelectBuilder).observe();
    }
  }

  static final class Builder {
    @NonNull
    final String sql;
    @Nullable
    String[] args;
    @NonNull
    String[] observedTables = EMPTY_STRINGS;
    @NonNull
    DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

    Builder(@NonNull String sql) {
      this.sql = sql;
    }
  }

  static final class CompiledRawSelectImpl extends Query.DatabaseQuery<Cursor, Cursor> implements CompiledObservableRawSelect {
    @NonNull
    final String sql;
    @Nullable
    final String[] args;
    @NonNull
    final String[] observedTables;

    CompiledRawSelectImpl(@NonNull Builder builder) {
      super(builder.dbConnection, null);
      this.sql = builder.sql;
      this.args = builder.args;
      this.observedTables = builder.observedTables;
    }

    @NonNull
    @Override
    Cursor rawQuery(boolean inStream) {
      super.rawQuery(inStream);
      final SupportSQLiteDatabase db = dbConnection.getReadableDatabase();
      final long startNanos = nanoTime();
      final Cursor cursor = db.query(sql, args);
      if (SqliteMagic.LOGGING_ENABLED) {
        final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
      }
      return FastCursor.tryCreate(cursor);
    }

    @Override
    Cursor map(@NonNull Cursor cursor) {
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
      return new SingleItemQueryObservable<>(createQueryObservable(observedTables, this));
    }

    @Override
    public String toString() {
      return "[RAW; sql=" + sql + "]";
    }
  }
}
