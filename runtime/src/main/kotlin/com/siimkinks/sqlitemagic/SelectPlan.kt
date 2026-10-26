package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.PreparedQuery
import com.siimkinks.sqlitemagic.internal.sql.bindNativeValues
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.functions.Function
import io.reactivex.functions.Predicate

/**
 * Compiled SQL SELECT statement.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Result type
 */
sealed class SelectPlan<T>(
  internal val prepared: PreparedQuery,
  private val arguments: List<NativeValue>?
) {
  /**
   * SQL of this statement.
   */
  val sql get() = prepared.sql

  internal fun execution(): Query<T> = PlanExecution(this)

  internal abstract fun mapRows(cursor: Cursor, isDisposed: () -> Boolean = NOT_DISPOSED): T
  internal open fun discard(result: T) = Unit

  internal fun boundArguments() = arguments ?: prepared.arguments(emptyList())

  internal fun openCursor(dbConnection: DbConnectionImpl, values: List<NativeValue> = boundArguments()): Cursor {
    val query = object : SupportSQLiteQuery {
      override val sql get() = prepared.sql
      override val argCount get() = values.size
      override fun bindTo(statement: SupportSQLiteProgram) = bindNativeValues(
        statement = statement,
        values = values
      )
    }
    return FastCursor
      .tryCreate(dbConnection.readableDatabase.query(query))
      .also(prepared::checkReadWidth)
  }

  internal fun executePlan() = mapRows(
    openCursor(
      values = boundArguments(),
      dbConnection = prepared.connection.orDefault()
    )
  )

  internal fun notifications(): Observable<Query<T>> {
    boundArguments()
    return Observable.defer {
      val execution = PlanExecution(this)
      val connection = execution.resolveConnection()
      val observedTables = prepared.dependencies.observedTables
      when {
        observedTables.isEmpty() -> Observable.just<Query<T>>(execution)
        else -> connection.triggers
          .filter(tableFilter(observedTables))
          .map(execution)
          .startWith(execution)
      }
        .observeOn(connection.queryScheduler)
        .doOnSubscribe(connection.ensureNotInTransaction)
    }
  }
}

private fun tableFilter(observedTables: Array<String>) = when {
  observedTables.size > 1 -> Predicate<Set<String>> { triggers ->
    observedTables.any(triggers::contains)
  }
  else -> observedTables[0].let { table ->
    Predicate<Set<String>> { triggers ->
      table in triggers
    }
  }
}

private class PlanExecution<T>(
  private val plan: SelectPlan<T>
) : Query<T>(plan.prepared.connection), Function<Set<String>, Query<T>> {
  override fun rawQuery(dbConnection: DbConnectionImpl) = plan.openCursor(dbConnection)

  override fun map(cursor: Cursor, isDisposed: () -> Boolean) = plan.mapRows(
    cursor = cursor,
    isDisposed = isDisposed
  )

  override fun discardResult(result: T) = plan.discard(result)

  override fun apply(ignored: Set<String>): Query<T> = this
}

/**
 * Compiled SQL select statement.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 */
class CompiledSelect<T> internal constructor(
  prepared: PreparedQuery,
  private val reader: ExecutionReader<T>,
  arguments: List<NativeValue>? = null
) : SelectPlan<List<T>>(
  prepared = prepared,
  arguments = arguments
) {
  /**
   * Bind values of the query parameters.
   *
   * Create parameters with `asParameter`.
   *
   * Example:
   * ```
   * val name = AUTHOR.NAME.asParameter("name")
   * val compiled = (SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS name))
   *     .compile()
   * val adas = compiled
   *     .bind(name TO "Ada")
   *     .execute()
   * ```
   *
   * @param assignments Values of all the query parameters
   * @return Compiled select statement with the bound values
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, or the query has no such
   *     parameter
   */
  fun bind(vararg assignments: ParameterAssignment) = CompiledSelect(
    prepared = prepared,
    reader = reader,
    arguments = prepared.bind(assignments.asList())
  )

  /**
   * Execute this compiled select statement against a database.
   *
   * Returned value will never be `null`. If query returns no rows then resulting list will be empty. This method runs
   * synchronously in the calling thread.
   *
   * @return Query result
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun execute(): List<T> = executePlan()

  /**
   * Create an observable which will notify subscribers with a [Query] for execution.
   *
   * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
   * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
   * longer want updates to a query.
   *
   * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
   * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
   * the immediate notification sent on subscribe also uses this scheduler. As such, calling
   * [Observable.subscribeOn] on the returned observable has no effect.
   *
   * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
   * `skip(1)` on the returned observable.
   *
   * One might want to explore the returned type methods for convenience query related operators.
   *
   * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
   * will the operation occur.
   *
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun observe() = ListQueryObservable(notifications())

  override fun mapRows(cursor: Cursor, isDisposed: () -> Boolean): List<T> = cursor.use { rows ->
    val read = reader.open()
    buildList(rows.count) {
      while (!isDisposed() && rows.moveToNext()) {
        add(read(rows, 0))
      }
    }
  }
}

/**
 * Compiled SQL select statement that takes only the first row of the result set.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 */
class CompiledFirst<T> internal constructor(
  prepared: PreparedQuery,
  private val reader: ExecutionReader<T>,
  arguments: List<NativeValue>? = null
) : SelectPlan<T?>(
  prepared = prepared,
  arguments = arguments
) {
  /**
   * Bind values of the query parameters.
   *
   * Create parameters with `asParameter`.
   *
   * @param assignments Values of all the query parameters
   * @return Compiled select statement with the bound values
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, or the query has no such
   *     parameter
   */
  fun bind(vararg assignments: ParameterAssignment) = CompiledFirst(
    prepared = prepared,
    reader = reader,
    arguments = prepared.bind(assignments.asList())
  )

  /**
   * Execute this compiled select statement against a database.
   *
   * Returned value will be `null` if the query returns no rows or the selected scalar value is `null`.
   * This method runs synchronously in the calling thread.
   *
   * @return Query result or `null` if query returns no rows or the selected scalar value is `null`
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun execute(): T? = executePlan()

  /**
   * Create an observable which will notify subscribers with a [Query] for execution.
   *
   * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
   * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
   * longer want updates to a query.
   *
   * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
   * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
   * the immediate notification sent on subscribe also uses this scheduler. As such, calling
   * [Observable.subscribeOn] on the returned observable has no effect.
   *
   * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
   * `skip(1)` on the returned observable.
   *
   * One might want to explore the returned type methods for convenience query related operators.
   *
   * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
   * will the operation occur.
   *
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun observe() = SingleItemQueryObservable(notifications())

  override fun mapRows(cursor: Cursor, isDisposed: () -> Boolean): T? = cursor.use { rows ->
    when {
      rows.moveToNext() -> reader.open()(rows, 0)
      else -> null
    }
  }
}

/**
 * Compiled SQL count select statement.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 */
class CompiledCount internal constructor(
  prepared: PreparedQuery,
  arguments: List<NativeValue>? = null
) : SelectPlan<Long>(
  prepared = prepared,
  arguments = arguments
) {
  /**
   * Bind values of the query parameters.
   *
   * Create parameters with `asParameter`.
   *
   * @param assignments Values of all the query parameters
   * @return Compiled select statement with the bound values
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, or the query has no such
   *     parameter
   */
  fun bind(vararg assignments: ParameterAssignment) = CompiledCount(
    prepared = prepared,
    arguments = prepared.bind(assignments.asList())
  )

  /**
   * Execute this compiled count select statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Query result
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun execute(): Long = executePlan()

  /**
   * Create an observable which will notify subscribers with a [Query] for execution.
   *
   * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
   * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
   * longer want updates to a query.
   *
   * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
   * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
   * the immediate notification sent on subscribe also uses this scheduler. As such, calling
   * [Observable.subscribeOn] on the returned observable has no effect.
   *
   * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
   * `skip(1)` on the returned observable.
   *
   * One might want to explore the returned type methods for convenience query related operators.
   *
   * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
   * will the operation occur.
   *
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun observe() = CountQueryObservable(notifications())

  override fun mapRows(cursor: Cursor, isDisposed: () -> Boolean): Long = cursor.use { rows ->
    rows.moveToNext()
    rows.getLong(0)
  }
}

/**
 * Compiled SQL select statement that returns query results in raw [Cursor] objects.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 */
class CompiledCursor<T> internal constructor(
  prepared: PreparedQuery,
  private val reader: ExecutionReader<T>,
  arguments: List<NativeValue>? = null
) : SelectPlan<Cursor>(
  prepared = prepared,
  arguments = arguments
) {
  /**
   * Bind values of the query parameters.
   *
   * Create parameters with `asParameter`.
   *
   * @param assignments Values of all the query parameters
   * @return Compiled select statement with the bound values
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, or the query has no such
   *     parameter
   */
  fun bind(vararg assignments: ParameterAssignment) = CompiledCursor(
    prepared = prepared,
    reader = reader,
    arguments = prepared.bind(assignments.asList())
  )

  /**
   * Execute this compiled select statement against a database.
   *
   * Caller of this method is responsible for **always** closing the returned [Cursor]. This method runs synchronously
   * in the calling thread.
   *
   * @return Cursor over the result set
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun execute(): Cursor = executePlan()

  /**
   * Create an observable which will notify subscribers with a [Query] for execution. Subscribers are responsible for
   * **always** closing [Cursor] instance returned from the [Query].
   *
   * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
   * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
   * longer want updates to a query.
   *
   * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
   * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
   * the immediate notification sent on subscribe also uses this scheduler. As such, calling
   * [Observable.subscribeOn] on the returned observable has no effect.
   *
   * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
   * `skip(1)` on the returned observable.
   *
   * One might want to explore the returned type methods for convenience query related operators.
   *
   * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
   * will the operation occur.
   *
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun observe() = SingleItemQueryObservable(notifications())

  /**
   * Read the row at the current cursor position.
   *
   * @param cursor Cursor returned by this statement
   * @return Row
   */
  fun getFromCurrentPosition(cursor: Cursor): T = reader.open()(cursor, 0)

  override fun mapRows(cursor: Cursor, isDisposed: () -> Boolean): Cursor = cursor
  override fun discard(result: Cursor) = result.close()
}
