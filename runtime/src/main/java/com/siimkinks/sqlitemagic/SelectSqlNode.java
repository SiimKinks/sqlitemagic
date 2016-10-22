package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation;

import java.util.List;

/**
 * A node in SELECT SQL builder.
 *
 * @param <S> Selection type -- either {@link Select.Select1} or {@link Select.SelectN}
 */
public abstract class SelectSqlNode<S> extends SqlNode {
  @NonNull
  final SelectBuilder<S> selectBuilder;

  SelectSqlNode(@Nullable SelectSqlNode<S> parent) {
    super(parent);
    final SelectBuilder<S> selectBuilder;
    if (parent != null) {
      selectBuilder = parent.selectBuilder;
    } else {
      selectBuilder = new SelectBuilder<>();
    }
    selectBuilder.sqlTreeRoot = this;
    selectBuilder.sqlNodeCount++;
    this.selectBuilder = selectBuilder;
  }

  public static abstract class SelectNode<T, S> extends SelectSqlNode<S> implements ConnectionProvidedOperation<SelectNode<T, S>> {
    SelectNode(SelectSqlNode<S> parent) {
      super(parent);
    }

    /**
     * Convert SELECT statement into column which can then be used as inner SELECT.
     *
     * @param alias Column alias
     * @return Column to be used in SELECT builder
     */
    @NonNull
    @CheckResult
    public final NumericColumn<T, T, T, ?> asColumn(@NonNull String alias) {
      return SelectionColumn.from(selectBuilder, alias);
    }

    /**
     * Mark that data should be queried deep.
     * <p>
     * If this method is called all complex columns and their complex columns will be queried.
     * If any join is missing then system adds them. It also respects user defined joins and
     * ignores any needed join if user has defined it.
     * <p>
     * By default system performs shallow queries, which means that even if user has defined
     * join on complex column, it will be ignored on result set parsing.
     * <p>
     * It is important to note that shallow query means that the resulting data will be
     * "as minimal as possible" -- if queried data is immutable and has non-null
     * immutable complex columns then result data will be filled respecting nullability
     * contracts.
     *
     * @return Builder for SQL SELECT statement.
     */
    @NonNull
    @CheckResult
    public final SelectNode<T, S> queryDeep() {
      selectBuilder.deep = true;
      return this;
    }

    @NonNull
    @Override
    public final SelectNode<T, S> usingConnection(@NonNull DbConnection connection) {
      selectBuilder.dbConnection = (DbConnectionImpl) connection;
      return this;
    }

    /**
     * Compile select builder.
     * <p>
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     * <p>
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @NonNull
    @CheckResult
    public final CompiledSelect<T, S> compile() {
      return selectBuilder.build();
    }

    /**
     * Compile select builder and instruct it to take only the first element from the
     * result set.
     * <p>
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     * <p>
     * It is important to note that this method also modifies the underlying SQL and
     * adds "LIMIT 1" clause. It will throw {@link android.database.SQLException SQLException}
     * if user has defined LIMIT clause with anything other than "1". If there is
     * already "LIMIT 1" clause then SQL will not be modified.
     * <p>
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @NonNull
    @CheckResult
    public final CompiledFirstSelect<T, S> takeFirst() {
      return selectBuilder.<T>build().takeFirst();
    }

    /**
     * Compile select builder and instruct it to count only the rows of the resulting
     * query.
     * <p>
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     * <p>
     * It is important to note that this method also modifies the underlying SQL and
     * adds "{@code count(*)}" function. It also discards any other columns defined in the
     * SQL builder. Resulting SQL will be "SELECT count(*) FROM ..."
     * <p>
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @NonNull
    @CheckResult
    public final CompiledCountSelect count() {
      return selectBuilder.<T>build().count();
    }

    /**
     * Compile select builder and instruct it to return raw
     * {@link android.database.Cursor Cursor} when query is executed.
     * <p>
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     * <p>
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @NonNull
    @CheckResult
    public final CompiledCursorSelect<T, S> toCursor() {
      return selectBuilder.<T>build().toCursor();
    }

    /**
     * Compile and execute this select statement against a database.
     * <p>
     * Returned value will never be {@code null}. If query returns no rows then resulting
     * list will be empty.<br>
     * This method runs synchronously in the calling thread.
     *
     * @return Query result
     */
    @NonNull
    @CheckResult
    @WorkerThread
    public final List<T> execute() {
      return selectBuilder.<T>build().execute();
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
     * observable use the {@link rx.Scheduler} supplied to
     * {@link com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder#scheduleRxQueriesOn}. For
     * consistency, the immediate notification sent on subscribe also uses this scheduler. As such,
     * calling {@link rx.Observable#subscribeOn subscribeOn} on the returned observable has no effect.
     * <p>
     * Note: To skip the immediate notification and only receive subsequent notifications when data
     * has changed call {@code skip(1)} on the returned observable.
     * <p>
     * One might want to explore the returned type methods for convenience query related
     * operators.
     * <p>
     * <b>Warning:</b> this method does not perform the query! Only by subscribing to the returned
     * {@link rx.Observable} will the operation occur.
     */
    @NonNull
    @CheckResult
    public final QueryObservable<List<T>> observe() {
      return selectBuilder.<T>build().observe();
    }
  }
}
