package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Query.DatabaseQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.siimkinks.sqlitemagic.CompiledSelectImpl.CompiledFirstSelectImpl.addTakeFirstLimitClauseIfNeeded;
import static com.siimkinks.sqlitemagic.CompiledSelectImpl.createQueryObservable;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class CompiledSelect1Impl<T, S> extends DatabaseQuery<List<T>, T> implements CompiledSelect<T, S> {
  @NonNull
  final String sql;
  @Nullable
  final String[] args;
  @NonNull
  final Column<?, T, ?, ?, ?> selectedColumn;
  @NonNull
  final String[] observedTables;

  CompiledSelect1Impl(@NonNull String sql,
                      @Nullable String[] args,
                      @NonNull DbConnectionImpl dbConnection,
                      @NonNull final Column<?, T, ?, ?, ?> selectedColumn,
                      @NonNull String[] observedTables) {
    super(dbConnection, new Mapper<T>() {
      @Override
      public T apply(@NonNull FastCursor cursor) {
        return selectedColumn.getFromCursor(cursor);
      }
    });
    this.sql = sql;
    this.args = args;
    this.selectedColumn = selectedColumn;
    this.observedTables = observedTables;
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
  List<T> map(@NonNull SqliteMagicCursor androidCursor) {
    try {
      final FastCursor cursor = androidCursor.getFastCursor();
      final int rowCount = cursor.getCount();
      if (rowCount == 0) {
        return Collections.emptyList();
      }
      final Column<?, T, ?, ?, ?> selectedColumn = this.selectedColumn;
      final ArrayList<T> values = new ArrayList<>(rowCount);
      while (cursor.moveToNext()) {
        values.add(selectedColumn.<T>getFromCursor(cursor));
      }
      return values;
    } finally {
      androidCursor.close();
    }
  }

  @Override
  public String toString() {
    return "[Select1<List>; sql=" + sql + "]";
  }

  @NonNull
  @Override
  public List<T> execute() {
    return map(rawQuery(false));
  }

  @NonNull
  @Override
  public ListQueryObservable<T> observe() {
    return new ListQueryObservable<>(createQueryObservable(observedTables, this));
  }

  @NonNull
  @Override
  public CompiledFirstSelect<T, S> takeFirst() {
    return new CompiledFirstSelect1Impl<>(this, dbConnection);
  }

  @NonNull
  @Override
  public CompiledCountSelect<S> count() {
    return new CompiledSelectImpl.CompiledCountSelectImpl<>(sql, args, dbConnection, observedTables);
  }

  @NonNull
  @Override
  public CompiledCursorSelect<T, S> toCursor() {
    return new CompiledCursorSelect1Impl<>(this, dbConnection);
  }

  static final class CompiledFirstSelect1Impl<T, S> extends DatabaseQuery<T, T> implements CompiledFirstSelect<T, S> {
    @NonNull
    private final SQLiteStatement selectStm;
    @NonNull
    final String sql;
    @Nullable
    final String[] args;
    @NonNull
    final Column<?, T, ?, ?, ?> selectedColumn;
    @NonNull
    final String[] observedTables;

    CompiledFirstSelect1Impl(@NonNull CompiledSelect1Impl<T, S> compiledSelect,
                             @NonNull DbConnectionImpl dbConnection) {
      super(dbConnection, null);
      final String sql = addTakeFirstLimitClauseIfNeeded(compiledSelect.sql);
      final String[] args = compiledSelect.args;
      final SQLiteStatement selectStm = dbConnection.compileStatement(sql);
      selectStm.bindAllArgsAsStrings(args);
      this.selectStm = selectStm;
      this.sql = sql;
      this.args = args;
      this.selectedColumn = compiledSelect.selectedColumn;
      this.observedTables = compiledSelect.observedTables;
    }

    @Override
    T map(@Nullable SqliteMagicCursor __) {
      return execute();
    }

    @Nullable
    @Override
    public T execute() {
      final T val;
      final long startNanos;
      synchronized (selectStm) {
        startNanos = nanoTime();
        val = selectedColumn.getFromStatement(selectStm);
      }
      if (SqliteMagic.LOGGING_ENABLED) {
        final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
      }
      return val;
    }

    @NonNull
    @Override
    public SingleItemQueryObservable<T> observe() {
      return new SingleItemQueryObservable<>(createQueryObservable(observedTables, this));
    }

    @Override
    public String toString() {
      return "[TAKE FIRST 1;sql=" + sql + "]";
    }
  }

  static final class CompiledCursorSelect1Impl<T, S> extends DatabaseQuery<Cursor, T> implements CompiledCursorSelect<T, S> {
    @NonNull
    private final String sql;
    @Nullable
    private final String[] args;
    @NonNull
    final Column<?, T, ?, ?, ?> selectedColumn;
    @NonNull
    private final String[] observedTables;

    CompiledCursorSelect1Impl(@NonNull CompiledSelect1Impl<T, S> compiledSelect,
                              @NonNull DbConnectionImpl dbConnection) {
      super(dbConnection, null);
      this.sql = compiledSelect.sql;
      this.args = compiledSelect.args;
      this.selectedColumn = compiledSelect.selectedColumn;
      this.observedTables = compiledSelect.observedTables;
    }

    @Nullable
    @Override
    public T getFromCurrentPosition(@NonNull Cursor cursor) {
      final FastCursor fastCursor = ((SqliteMagicCursor) cursor).getFastCursorAndSync();
      return selectedColumn.getFromCursor(fastCursor);
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
      return new SingleItemQueryObservable<>(createQueryObservable(observedTables, this));
    }

    @Override
    public String toString() {
      return "[CURSOR 1; sql=" + sql + "]";
    }
  }
}
