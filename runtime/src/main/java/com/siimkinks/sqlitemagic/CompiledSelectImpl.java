package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.util.MutableInt;

import java.util.List;
import java.util.Set;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

// TODO optimize string allocations
final class CompiledSelectImpl<T, S> extends Query<List<T>> implements CompiledSelect<T, S> {
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
		super(dbConnection);
		this.sql = sql;
		this.args = args;
		this.table = table;
		this.observedTables = observedTables;
		this.columns = columns;
		this.tableGraphNodeNames = tableGraphNodeNames;
		this.queryDeep = queryDeep;
	}

	@NonNull
	@CheckResult
	@Override
	List<T> runImpl(@NonNull Subscription subscription, boolean inStream) {
		super.runImpl(subscription, inStream);
		final SQLiteDatabase db = dbConnection.getReadableDatabase();
		SqliteMagicCursor cursor = null;
		try {
			final long startNanos = nanoTime();
			cursor = (SqliteMagicCursor) db.rawQueryWithFactory(null, sql, args, null, null);
			if (SqliteMagic.LOGGING_ENABLED) {
				final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
				LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
			}
			return table.allFromCursor(cursor.getFastCursor(), columns, tableGraphNodeNames, queryDeep, subscription);
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public String toString() {
		return "[deepQuery=" + queryDeep + ";sql=" + sql + "]";
	}

	@NonNull
	@CheckResult
	@WorkerThread
	@Override
	public List<T> execute() {
		return runImpl(INFINITE_SUBSCRIPTION, false);
	}

	@NonNull
	@CheckResult
	@Override
	public QueryObservable<List<T>> observe() {
		return new QueryObservable<>(createQueryObservable(observedTables, (Query<List<T>>) this));
	}

	@NonNull
	@CheckResult
	@Override
	public CompiledFirstSelect<T, S> takeFirst() {
		return new CompiledFirstSelectImpl<>(this, dbConnection);
	}

	@NonNull
	@CheckResult
	@Override
	public CompiledCountSelect<S> count() {
		return new CompiledCountSelectImpl<>(sql, args, dbConnection, observedTables);
	}

	@NonNull
	@CheckResult
	@Override
	public CompiledCursorSelect<T, S> toCursor() {
		return new CompiledCursorSelectImpl<>(this, dbConnection);
	}

	static final class CompiledCountSelectImpl<S> extends Query<Long> implements CompiledCountSelect<S> {
		@NonNull
		private final SQLiteStatement countStm;
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
			super(dbConnection);
			final String sql = addCountFunction(parentSql);
			final SQLiteStatement countStm = dbConnection.compileStatement(sql);
			countStm.bindAllArgsAsStrings(args);
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

		@NonNull
		@Override
		Long runImpl(@NonNull Subscription subscriber, boolean inStream) {
			super.runImpl(subscriber, inStream);
			return execute();
		}

		@CheckResult
		@WorkerThread
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
		@CheckResult
		@Override
		public CountQueryObservable observe() {
			return new CountQueryObservable(createQueryObservable(observedTables, (Query<Long>) this));
		}

		@Override
		public String toString() {
			return "[COUNT; sql=" + sql + "]";
		}
	}

	static final class CompiledCursorSelectImpl<T, S> extends Query<Cursor> implements CompiledCursorSelect<T, S> {
		@NonNull
		private final String sql;
		@Nullable
		private final String[] args;
		@NonNull
		private final Table<T> table;
		@NonNull
		private final String[] observedTables;
		@Nullable
		private final SimpleArrayMap<String, Integer> columns;
		@Nullable
		private final SimpleArrayMap<String, String> tableGraphNodeNames;
		private final boolean queryDeep;
		@NonNull
		private final MutableInt columnOffset = new MutableInt();

		CompiledCursorSelectImpl(@NonNull CompiledSelectImpl<T, S> compiledSelect,
		                         @NonNull DbConnectionImpl dbConnection) {
			super(dbConnection);
			this.sql = compiledSelect.sql;
			this.args = compiledSelect.args;
			this.table = compiledSelect.table;
			this.observedTables = compiledSelect.observedTables;
			this.columns = compiledSelect.columns;
			this.tableGraphNodeNames = compiledSelect.tableGraphNodeNames;
			this.queryDeep = compiledSelect.queryDeep;
		}

		@Nullable
		@CheckResult
		@WorkerThread
		@Override
		public T getFromCurrentPosition(@NonNull Cursor cursor) {
			columnOffset.value = 0;
			return table.fromCurrentCursorPosition(((SqliteMagicCursor) cursor).getFastCursorAndSync(), columns, tableGraphNodeNames, queryDeep, columnOffset);
		}

		@NonNull
		@Override
		Cursor runImpl(@NonNull Subscription subscriber, boolean inStream) {
			super.runImpl(subscriber, inStream);
			final SQLiteDatabase db = dbConnection.getReadableDatabase();
			final long startNanos = nanoTime();
			final Cursor cursor = db.rawQueryWithFactory(null, sql, args, null, null);
			if (SqliteMagic.LOGGING_ENABLED) {
				final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
				LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
			}
			return cursor;
		}

		@NonNull
		@CheckResult
		@WorkerThread
		@Override
		public Cursor execute() {
			return runImpl(INFINITE_SUBSCRIPTION, false);
		}

		@NonNull
		@CheckResult
		@Override
		public QueryObservable<Cursor> observe() {
			return new QueryObservable<>(createQueryObservable(observedTables, (Query<Cursor>) this));
		}

		@Override
		public String toString() {
			return "[CURSOR; deepQuery=" + queryDeep + ";sql=" + sql + "]";
		}
	}

	static final class CompiledFirstSelectImpl<T, S> extends Query<T> implements CompiledFirstSelect<T, S> {
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
			super(dbConnection);
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

		@Nullable
		@Override
		T runImpl(@NonNull Subscription subscriber, boolean inStream) {
			super.runImpl(subscriber, inStream);
			final SQLiteDatabase db = dbConnection.getReadableDatabase();
			SqliteMagicCursor cursor = null;
			try {
				final long startNanos = nanoTime();
				cursor = (SqliteMagicCursor) db.rawQueryWithFactory(null, sql, args, null, null);
				if (SqliteMagic.LOGGING_ENABLED) {
					final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
					LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
				}
				return table.firstFromCursor(cursor.getFastCursor(), columns, tableGraphNodeNames, queryDeep);
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}

		@Nullable
		@CheckResult
		@WorkerThread
		@Override
		public T execute() {
			return runImpl(INFINITE_SUBSCRIPTION, false);
		}

		@NonNull
		@CheckResult
		@Override
		public QueryObservable<T> observe() {
			return new QueryObservable<>(createQueryObservable(observedTables, (Query<T>) this));
		}

		@Override
		public String toString() {
			return "[TAKE FIRST; deepQuery=" + queryDeep + ";sql=" + sql + "]";
		}
	}

	@NonNull
	@CheckResult
	static <Q extends Query> Observable<Q> createQueryObservable(@NonNull final String[] observedTables,
	                                                             @NonNull final Q query) {
		final Func1<Set<String>, Boolean> tableFilter;
		if (observedTables.length > 1) {
			tableFilter = new Func1<Set<String>, Boolean>() {
				@Override
				public Boolean call(Set<String> triggers) {
					for (String table : observedTables) {
						if (triggers.contains(table)) {
							return Boolean.TRUE;
						}
					}
					return Boolean.FALSE;
				}
			};
		} else {
			final String table = observedTables[0];
			tableFilter = new Func1<Set<String>, Boolean>() {
				@Override
				public Boolean call(Set<String> triggers) {
					return triggers.contains(table);
				}
			};
		}
		final DbConnectionImpl dbConnectionImpl = query.dbConnection;
		return dbConnectionImpl.triggers
				.filter(tableFilter) // Only trigger on tables we care about.
				.map(new Func1<Set<String>, Q>() {
					@Override
					public Q call(Set<String> triggers) {
						return query;
					}
				})
				.onBackpressureLatest() // Guard against uncontrollable frequency of upstream emissions.
				.startWith(query)
				.observeOn(dbConnectionImpl.queryScheduler)
				.onBackpressureLatest() // Guard against uncontrollable frequency of scheduler executions.
				.doOnSubscribe(new Action0() {
					@Override
					public void call() {
						if (dbConnectionImpl.transactions.get() != null) {
							throw new IllegalStateException("Cannot subscribe to observable query in a transaction.");
						}
					}
				});
	}
}
