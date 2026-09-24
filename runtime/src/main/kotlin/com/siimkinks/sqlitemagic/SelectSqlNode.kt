package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import com.siimkinks.sqlitemagic.Select.CompoundSelect
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation

/**
 * A node in SELECT SQL builder.
 *
 * @param S Selection type -- either [Select.Select1] or [Select.SelectN]
 */
abstract class SelectSqlNode<S> internal constructor(
  parent: SelectSqlNode<S>?
) : SqlNode(parent) {
  internal val selectBuilder: SelectBuilder<S>

  init {
    val selectBuilder = parent?.selectBuilder ?: SelectBuilder()
    selectBuilder.sqlTreeRoot = this
    selectBuilder.sqlNodeCount++
    this.selectBuilder = selectBuilder
  }

  abstract class SelectNode<T, S, N> internal constructor(
    parent: SelectSqlNode<S>?
  ) : SelectSqlNode<S>(parent), ConnectionProvidedOperation<SelectNode<T, S, N>> {
    /**
     * Connect together simple SELECT statements to form a compound SELECT using the UNION operator.
     *
     * The UNION operator works the same way as UNION ALL, except that duplicate rows
     * are removed from the final result set.
     *
     * In a compound SELECT, all the constituent SELECTs must return the same number of
     * result columns. As the components of a compound SELECT must be simple SELECT
     * statements, they may not contain ORDER BY or LIMIT clauses. ORDER BY and LIMIT
     * clauses may only occur at the end of the entire compound SELECT, and then only
     * if the final element of the compound is not a VALUES clause.
     *
     * @param select Connected simple SELECT statement to form a compound SELECT
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun union(select: SelectNode<*, S, *>) = CompoundSelect(this, "UNION", select)

    /**
     * Connect together simple SELECT statements to form a compound SELECT using the UNION ALL operator.
     *
     * A compound SELECT created using UNION ALL operator returns all the rows from the SELECT
     * to the left of the UNION ALL operator, and all the rows from the SELECT to the right of it.
     *
     * In a compound SELECT, all the constituent SELECTs must return the same number of
     * result columns. As the components of a compound SELECT must be simple SELECT
     * statements, they may not contain ORDER BY or LIMIT clauses. ORDER BY and LIMIT
     * clauses may only occur at the end of the entire compound SELECT, and then only
     * if the final element of the compound is not a VALUES clause.
     *
     * @param select Connected simple SELECT statement to form a compound SELECT
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun unionAll(select: SelectNode<*, S, *>) = CompoundSelect(this, "UNION ALL", select)

    /**
     * Connect together simple SELECT statements to form a compound SELECT using the INTERSECT operator.
     *
     * The INTERSECT operator returns the intersection of the results of the left and right SELECTs.
     * Duplicate rows are removed from the results before the result set is returned.
     *
     * In a compound SELECT, all the constituent SELECTs must return the same number of
     * result columns. As the components of a compound SELECT must be simple SELECT
     * statements, they may not contain ORDER BY or LIMIT clauses. ORDER BY and LIMIT
     * clauses may only occur at the end of the entire compound SELECT, and then only
     * if the final element of the compound is not a VALUES clause.
     *
     * @param select Connected simple SELECT statement to form a compound SELECT
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun intersect(select: SelectNode<*, S, *>) = CompoundSelect(this, "INTERSECT", select)

    /**
     * Connect together simple SELECT statements to form a compound SELECT using the EXCEPT operator.
     *
     * The EXCEPT operator returns the subset of rows returned by the left SELECT
     * that are not also returned by the right-hand SELECT. Duplicate rows are
     * removed from the results before the result set is returned.
     *
     * In a compound SELECT, all the constituent SELECTs must return the same number of
     * result columns. As the components of a compound SELECT must be simple SELECT
     * statements, they may not contain ORDER BY or LIMIT clauses. ORDER BY and LIMIT
     * clauses may only occur at the end of the entire compound SELECT, and then only
     * if the final element of the compound is not a VALUES clause.
     *
     * @param select Connected simple SELECT statement to form a compound SELECT
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun except(select: SelectNode<*, S, *>) = CompoundSelect(this, "EXCEPT", select)

    /**
     * Convert SELECT statement into column which can then be used as inner SELECT.
     *
     * @param alias Column alias
     * @return Column to be used in SELECT builder
     */
    @CheckResult
    fun toColumn(alias: String): NumericColumn<T, T, T, *, N> = SelectionColumn.from(
      selectBuilder = selectBuilder,
      alias = alias
    )

    /**
     * Convert SELECT statement into table which can then be used as inner SELECT.
     *
     * @param alias Table alias
     * @return Table to be used in SELECT builder
     */
    @CheckResult
    fun toTable(alias: String): Table<T> = SelectionTable.from(
      selectTableBuilder = selectBuilder,
      alias = alias
    )

    /**
     * Mark that data should be queried deep.
     *
     * If this method is called all complex columns and their complex columns will be queried.
     * If any join is missing then system adds them. It also respects user defined joins and
     * ignores any needed join if user has defined it.
     *
     * By default system performs shallow queries, which means that even if user has defined
     * join on complex column, it will be ignored on result set parsing.
     *
     * It is important to note that shallow query means that the resulting data will be
     * "as minimal as possible" -- if queried data is immutable and has non-null
     * immutable complex columns then result data will be filled respecting nullability
     * contracts.
     *
     * @return Builder for SQL SELECT statement.
     */
    @CheckResult
    fun queryDeep() = apply {
      selectBuilder.deep = true
    }

    final override fun usingConnection(connection: DbConnection) = apply {
      selectBuilder.dbConnection = connection as DbConnectionImpl
    }

    /**
     * Compile select builder.
     *
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     *
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @CheckResult
    fun compile(): CompiledSelect<T, S> = selectBuilder.build()

    /**
     * Compile select builder and instruct it to take only the first element from the
     * result set.
     *
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     *
     * It is important to note that this method also modifies the underlying SQL and
     * adds "LIMIT 1" clause. It will throw [android.database.SQLException] if user has
     * defined LIMIT clause with anything other than "1". If there is already "LIMIT 1"
     * clause then SQL will not be modified.
     *
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @CheckResult
    fun takeFirst() = selectBuilder.build<T>().takeFirst()

    /**
     * Compile select builder and instruct it to count only the rows of the resulting
     * query.
     *
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     *
     * It is important to note that this method also modifies the underlying SQL and
     * adds "count(*)" function. It also discards any other columns defined in the
     * SQL builder. Resulting SQL will be "SELECT count(*) FROM ..."
     *
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @CheckResult
    fun count() = selectBuilder.build<T>().count()

    /**
     * Compile select builder and instruct it to return raw
     * [android.database.Cursor] when query is executed.
     *
     * Result is immutable object which can be shared across multiple threads
     * without side effects.
     *
     * NB! This method does not compile the underlying SQL statement against a database.
     *
     * @return Immutable compiled select statement
     */
    @CheckResult
    fun toCursor() = selectBuilder.build<T>().toCursor()

    /**
     * Compile and execute this select statement against a database.
     *
     * Returned value will never be `null`. If query returns no rows then resulting
     * list will be empty.
     * This method runs synchronously in the calling thread.
     *
     * @return Query result
     */
    @CheckResult
    @WorkerThread
    fun execute() = selectBuilder.build<T>().execute()

    /**
     * Create an observable which will notify subscribers with a [Query] for
     * execution.
     *
     * Subscribers will receive an immediate notification for initial data as well as subsequent
     * notifications for when the supplied `table`'s data changes through the SqliteMagic
     * provided model operations. Unsubscribe when you no longer want updates to a query.
     *
     * Since database triggers are inherently asynchronous, items emitted from the returned
     * observable use the [com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]
     * supplied scheduler. For consistency, the immediate notification sent on subscribe also uses this
     * scheduler. As such, calling [io.reactivex.Observable.subscribeOn] on the returned observable has no effect.
     *
     * Note: To skip the immediate notification and only receive subsequent notifications when
     * the data has changed call `skip(1)` on the returned observable.
     *
     * One might want to explore the returned type methods for convenience query related
     * operators.
     *
     * **Warning:** this method does not perform the query! Only by subscribing to the returned
     * [io.reactivex.Observable] will the operation occur.
     */
    @CheckResult
    fun observe() = selectBuilder.build<T>().observe()
  }
}
