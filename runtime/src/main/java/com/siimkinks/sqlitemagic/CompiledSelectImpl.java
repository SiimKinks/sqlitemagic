package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteStatement;
import android.database.Cursor;
import android.database.SQLException;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Query.DatabaseQuery;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.functions.Predicate;

import static com.siimkinks.sqlitemagic.SqlUtil.bindAllArgsAsStrings;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class CompiledSelectImpl<T, S> extends DatabaseQuery<List<T>, T> implements CompiledSelect<T, S> {
  @NonNull
  final String sql;
  @Nullable
  final String[] args;
  @NonNull
  final Table<T> table;
  @NonNull
  final String[] observedTables;
  @Nullable
  final SimpleArrayMap<String, Integer> columns;
  @Nullable
  final SimpleArrayMap<String, String> tableGraphNodeNames;
  final boolean queryDeep;

  CompiledSelectImpl(@NonNull String sql,
                     @Nullable String[] args,
                     @NonNull Table<T> table,
                     @NonNull DbConnectionImpl dbConnection,
                     @NonNull String[] observedTables,
                     @Nullable SimpleArrayMap<String, Integer> columns,
                     @Nullable SimpleArrayMap<String, String> tableGraphNodeNames,
                     boolean queryDeep) {
    super(dbConnection, table.mapper(columns, tableGraphNodeNames, queryDeep));
    this.sql = sql;
    this.args = args;
    this.table = table;
    this.observedTables = observedTables;
    this.columns = columns;
    this.tableGraphNodeNames = tableGraphNodeNames;
    this.queryDeep = queryDeep;
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
  List<T> map(@NonNull Cursor cursor) {
    try {
      final int rowCount = cursor.getCount();
      if (rowCount == 0) {
        return Collections.emptyList();
      }
      final Mapper<T> mapper = this.mapper;
      final ArrayList<T> values = new ArrayList<>(rowCount);
      while (cursor.moveToNext()) {
        //noinspection ConstantConditions -- mapper is not null here
        values.add(mapper.apply(cursor));
      }
      return values;
    } finally {
      cursor.close();
    }
  }

  @Override
  public String toString() {
    return "[deepQuery=" + queryDeep + ";sql=" + sql + "]";
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
    return new CompiledFirstSelectImpl<>(this, dbConnection);
  }

  @NonNull
  @Override
  public CompiledCountSelect<S> count() {
    return new CompiledCountSelectImpl<>(sql, args, dbConnection, observedTables);
  }

  @NonNull
  @Override
  public CompiledCursorSelect<T, S> toCursor() {
    return new CompiledCursorSelectImpl<>(this, dbConnection);
  }

  static final class CompiledCountSelectImpl<S> extends DatabaseQuery<Long, Long> implements CompiledCountSelect<S> {
    @NonNull
    private final SupportSQLiteStatement countStm;
    @NonNull
    private final String sql;
    @NonNull
    private final String[] observedTables;
    @Nullable
    private final String[] args;

    CompiledCountSelectImpl(@NonNull String parentSql,
                            @Nullable String[] args,
                            @NonNull DbConnectionImpl dbConnection,
                            @NonNull String[] observedTables) {
      super(dbConnection, null);
      final String sql = addCountFunction(parentSql);
      final SupportSQLiteStatement countStm = dbConnection.compileStatement(sql);
      bindAllArgsAsStrings(countStm, args);
      this.countStm = countStm;
      this.sql = sql;
      this.observedTables = observedTables;
      this.args = args;
    }

    @NonNull
    private static String addCountFunction(@NonNull String sql) {
      final int selectEndIndex = sql.indexOf("SELECT") + 6;
      final int fromStartIndex = sql.indexOf("FROM");
      final StringBuilder sb = new StringBuilder(sql.length());
      sb.append(sql.substring(0, selectEndIndex));
      sb.append(" count(*) ");
      sb.append(sql.substring(fromStartIndex));
      return sb.toString();
    }

    @Override
    Long map(Cursor __) {
      return execute();
    }

    @Override
    public long execute() {
      final long count;
      final long startNanos;
      synchronized (countStm) {
        startNanos = nanoTime();
        count = countStm.simpleQueryForLong();
      }
      if (SqliteMagic.LOGGING_ENABLED) {
        final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
        LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
      }
      return count;
    }

    @NonNull
    @Override
    public CountQueryObservable observe() {
      return new CountQueryObservable(createQueryObservable(observedTables, this));
    }

    @Override
    public String toString() {
      return "[COUNT; sql=" + sql + "]";
    }
  }

  static final class CompiledCursorSelectImpl<T, S> extends DatabaseQuery<Cursor, T> implements CompiledCursorSelect<T, S> {
    @NonNull
    private final String sql;
    @Nullable
    private final String[] args;
    @NonNull
    private final String[] observedTables;
    private final boolean queryDeep;

    CompiledCursorSelectImpl(@NonNull CompiledSelectImpl<T, S> compiledSelect,
                             @NonNull DbConnectionImpl dbConnection) {
      super(dbConnection, compiledSelect.mapper);
      this.sql = compiledSelect.sql;
      this.args = compiledSelect.args;
      this.observedTables = compiledSelect.observedTables;
      this.queryDeep = compiledSelect.queryDeep;
    }

    @SuppressWarnings("ConstantConditions") // -- mapper is not null here
    @Nullable
    @Override
    public T getFromCurrentPosition(@NonNull Cursor cursor) {
      if (cursor instanceof FastCursor) {
        final FastCursor fastCursor = (FastCursor) cursor;
        fastCursor.syncWith(cursor);
        return mapper.apply(fastCursor);
      }
      return mapper.apply(cursor);
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
      return "[CURSOR; deepQuery=" + queryDeep + ";sql=" + sql + "]";
    }
  }

  static final class CompiledFirstSelectImpl<T, S> extends DatabaseQuery<T, T> implements CompiledFirstSelect<T, S> {
    @NonNull
    final String sql;
    @Nullable
    final String[] args;
    @NonNull
    final Table<T> table;
    @NonNull
    final String[] observedTables;
    @Nullable
    final SimpleArrayMap<String, Integer> columns;
    @Nullable
    final SimpleArrayMap<String, String> tableGraphNodeNames;
    final boolean queryDeep;

    CompiledFirstSelectImpl(@NonNull CompiledSelectImpl<T, S> compiledSelect,
                            @NonNull DbConnectionImpl dbConnection) {
      super(dbConnection, compiledSelect.mapper);
      this.sql = addTakeFirstLimitClauseIfNeeded(compiledSelect.sql);
      this.args = compiledSelect.args;
      this.table = compiledSelect.table;
      this.observedTables = compiledSelect.observedTables;
      this.columns = compiledSelect.columns;
      this.tableGraphNodeNames = compiledSelect.tableGraphNodeNames;
      this.queryDeep = compiledSelect.queryDeep;
    }

    @NonNull
    static String addTakeFirstLimitClauseIfNeeded(@NonNull String sql) {
      final int limitIndex = sql.lastIndexOf("LIMIT");
      if (limitIndex != -1) {
        if (" 1 ".equals(sql.substring(limitIndex + 5, limitIndex + 8))) {
          return sql;
        } else {
          throw new SQLException("Tried to query first row but limit clause is already defined with limitClause != 1");
        }
      }
      return sql + "LIMIT 1 ";
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
    T map(@NonNull Cursor cursor) {
      try {
        if (cursor.moveToNext()) {
          //noinspection ConstantConditions -- mapper is not null here
          return mapper.apply(cursor);
        }
        return null;
      } finally {
        cursor.close();
      }
    }

    @Nullable
    @Override
    public T execute() {
      return map(rawQuery(false));
    }

    @NonNull
    @Override
    public SingleItemQueryObservable<T> observe() {
      return new SingleItemQueryObservable<>(createQueryObservable(observedTables, this));
    }

    @Override
    public String toString() {
      return "[TAKE FIRST; deepQuery=" + queryDeep + ";sql=" + sql + "]";
    }
  }

  @NonNull
  @CheckResult
  static <Q extends DatabaseQuery<T, E>, T, E> Observable<Query<T>> createQueryObservable(@NonNull final String[] observedTables,
                                                                                          @NonNull final Q query) {
    final Predicate<Set<String>> tableFilter;
    if (observedTables.length > 1) {
      tableFilter = new Predicate<Set<String>>() {
        @Override
        public boolean test(Set<String> triggers) {
          for (String table : observedTables) {
            if (triggers.contains(table)) {
              return true;
            }
          }
          return false;
        }
      };
    } else {
      final String table = observedTables[0];
      tableFilter = new Predicate<Set<String>>() {
        @Override
        public boolean test(Set<String> triggers) {
          return triggers.contains(table);
        }
      };
    }
    final DbConnectionImpl dbConnectionImpl = query.dbConnection;
    return dbConnectionImpl.triggers
        .filter(tableFilter) // Only trigger on tables we care about.
        .map(query)
        .startWith(query)
        .observeOn(dbConnectionImpl.queryScheduler)
        .doOnSubscribe(dbConnectionImpl.ensureNotInTransaction);
  }
}
