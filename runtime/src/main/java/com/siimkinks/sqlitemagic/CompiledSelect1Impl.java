package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

import rx.Subscription;

import static com.siimkinks.sqlitemagic.CompiledSelectImpl.CompiledFirstSelectImpl.addTakeFirstLimitClauseIfNeeded;
import static com.siimkinks.sqlitemagic.CompiledSelectImpl.createQueryObservable;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class CompiledSelect1Impl<T, S> extends Query<List<T>> implements CompiledSelect<T, S> {
	@NonNull
	final String sql;
	@Nullable
	final String[] args;
	@NonNull
	final Column<?, T, ?, ?> selectedColumn;
	@NonNull
	final String[] observedTables;

	CompiledSelect1Impl(@NonNull String sql,
	                    @Nullable String[] args,
	                    @NonNull DbConnectionImpl dbConnection,
	                    @NonNull Column<?, T, ?, ?> selectedColumn,
	                    @NonNull String[] observedTables) {
		super(dbConnection);
		this.sql = sql;
		this.args = args;
		this.selectedColumn = selectedColumn;
		this.observedTables = observedTables;
	}

	@NonNull
	@Override
	List<T> runImpl(@NonNull Subscription subscription, boolean inStream) {
		super.runImpl(subscription, inStream);
		final SQLiteDatabase db = dbConnection.getReadableDatabase();
		SqliteMagicCursor androidCursor = null;
		try {
			final long startNanos = nanoTime();
			androidCursor = (SqliteMagicCursor) db.rawQueryWithFactory(null, sql, args, null, null);
			if (SqliteMagic.LOGGING_ENABLED) {
				final long queryTimeInMillis = NANOSECONDS.toMillis(nanoTime() - startNanos);
				LogUtil.logQueryTime(queryTimeInMillis, observedTables, sql, args);
			}
			final FastCursor cursor = androidCursor.getFastCursor();
			final int rowCount = cursor.getCount();
			if (rowCount == 0) {
				return new ArrayList<>();
			}
			final ArrayList<T> values = new ArrayList<>(rowCount);
			final Column<?, T, ?, ?> selectedColumn = this.selectedColumn;
			while (cursor.moveToNext() && !subscription.isUnsubscribed()) {
				values.add(selectedColumn.<T>getFromCursor(cursor));
			}
			return values;
		} finally {
			if (androidCursor != null) {
				androidCursor.close();
			}
		}
	}

	@Override
	public String toString() {
		return "[Select1<List>; sql=" + sql + "]";
	}

	@NonNull
	@Override
	public List<T> execute() {
		return runImpl(INFINITE_SUBSCRIPTION, false);
	}

	@NonNull
	@Override
	public QueryObservable<List<T>> observe() {
		return new QueryObservable<>(createQueryObservable(observedTables, (Query<List<T>>) this));
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

	static final class CompiledFirstSelect1Impl<T, S> extends Query<T> implements CompiledFirstSelect<T, S> {
		@NonNull
		private final SQLiteStatement selectStm;
		@NonNull
		final String sql;
		@Nullable
		final String[] args;
		@NonNull
		final Column<?, T, ?, ?> selectedColumn;
		@NonNull
		final String[] observedTables;

		CompiledFirstSelect1Impl(@NonNull CompiledSelect1Impl<T, S> compiledSelect,
		                         @NonNull DbConnectionImpl dbConnection) {
			super(dbConnection);
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
		T runImpl(@NonNull Subscription subscriber, boolean inStream) {
			super.runImpl(subscriber, inStream);
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

		@Override
		public String toString() {
			return "[TAKE FIRST 1;sql=" + sql + "]";
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
	}

	static final class CompiledCursorSelect1Impl<T, S> extends Query<Cursor> implements CompiledCursorSelect<T, S> {
		@NonNull
		private final String sql;
		@Nullable
		private final String[] args;
		@NonNull
		final Column<?, T, ?, ?> selectedColumn;
		@NonNull
		private final String[] observedTables;

		CompiledCursorSelect1Impl(@NonNull CompiledSelect1Impl<T, S> compiledSelect,
		                          @NonNull DbConnectionImpl dbConnection) {
			super(dbConnection);
			this.sql = compiledSelect.sql;
			this.args = compiledSelect.args;
			this.selectedColumn = compiledSelect.selectedColumn;
			this.observedTables = compiledSelect.observedTables;
		}

		@Nullable
		@CheckResult
		@WorkerThread
		@Override
		public T getFromCurrentPosition(@NonNull Cursor cursor) {
			final FastCursor fastCursor = ((SqliteMagicCursor) cursor).getFastCursorAndSync();
			return selectedColumn.getFromCursor(fastCursor);
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
			return "[CURSOR 1; sql=" + sql + "]";
		}
	}
}
