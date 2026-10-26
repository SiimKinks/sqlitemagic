package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException
import androidx.annotation.CheckResult
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single

/**
 * Compiled raw SQL query.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
class CompiledRawSelect<T, O : RawTables> internal constructor(internal val plan: CompiledSelect<T>) {
  /**
   * SQL of this statement.
   */
  val sql get() = plan.sql

  /**
   * Execute this compiled query against a database.
   *
   * Returned value will never be `null`. If query returns no rows then resulting list will be empty.
   * This method runs synchronously in the calling thread.
   *
   * @return Query result
   * @throws IllegalStateException if the query returns another number of columns than `readAs` reads
   * @throws SQLException if a column read with a not nullable type holds SQL NULL
   */
  fun execute(): List<T> = plan.execute()
}

/**
 * Compiled raw SQL query that takes only the first row of the result set.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
class CompiledRawFirst<T, O : RawTables> internal constructor(internal val plan: CompiledFirst<T>) {
  /**
   * SQL of this statement.
   */
  val sql get() = plan.sql

  /**
   * Execute this compiled query against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Query result or `null` if query returns no rows or the read value is `null`
   * @throws IllegalStateException if the query returns another number of columns than `readAs` reads
   * @throws SQLException if a column read with a not nullable type holds SQL NULL
   */
  fun execute(): T? = plan.execute()
}

/**
 * Compiled raw SQL query that returns query results in raw [Cursor] objects.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
open class CompiledRawCursor<O : RawTables> internal constructor(internal val plan: CompiledCursor<*>) {
  /**
   * SQL of this statement.
   */
  val sql get() = plan.sql

  /**
   * Execute this compiled query against a database.
   *
   * Caller of this method is responsible for **always** closing the returned [Cursor]. This method runs synchronously
   * in the calling thread.
   *
   * @return Cursor over the result set
   * @throws IllegalStateException if the query returns another number of columns than `readAs` reads
   */
  fun execute(): Cursor = plan.execute()
}

/**
 * Compiled raw SQL query that returns query results in raw [Cursor] objects and reads their rows.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
class CompiledRawReadCursor<T, O : RawTables> internal constructor(
  private val readPlan: CompiledCursor<T>
) : CompiledRawCursor<O>(readPlan) {
  /**
   * Read the row at the current cursor position.
   *
   * @param cursor Cursor returned by this query
   * @return Row
   * @throws SQLException if a column read with a not nullable type holds SQL NULL
   */
  fun getFromCurrentPosition(cursor: Cursor) = readPlan.getFromCurrentPosition(cursor)
}

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
 * the immediate notification sent on subscribe also uses this scheduler. As such, calling
 * [Observable.subscribeOn] on the returned observable has no effect.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawRows")
fun <T> CompiledRawSelect<T, DeclaredTables>.observe() = plan.observe()

/**
 * Create a [Single] that when subscribed to executes this query against a database and emits its result to
 * downstream only once.
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to
 * [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn], so calling `subscribeOn` has no effect.
 *
 * @return Deferred [Single] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawRows")
fun <T> CompiledRawSelect<T, UndeclaredTables>.observe() = plan
  .observe()
  .runQueryOnce()

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
 * the immediate notification sent on subscribe also uses this scheduler. As such, calling
 * [Observable.subscribeOn] on the returned observable has no effect.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawFirst")
fun <T> CompiledRawFirst<T, DeclaredTables>.observe() = plan.observe()

/**
 * Create a [Maybe] that when subscribed to executes this query against a database and emits the first row to
 * downstream only once. It completes without a value if the query returns no rows or the read value is `null`.
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to
 * [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn], so calling `subscribeOn` has no effect.
 *
 * @return Deferred [Maybe] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawFirst")
fun <T> CompiledRawFirst<T, UndeclaredTables>.observe() = plan
  .observe()
  .runQueryOnce()

/**
 * Create an observable which will notify subscribers with a [Query] for execution. Subscribers are responsible for
 * **always** closing [Cursor] instance returned from the [Query].
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
 * the immediate notification sent on subscribe also uses this scheduler. As such, calling
 * [Observable.subscribeOn] on the returned observable has no effect.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawCursor")
fun CompiledRawCursor<DeclaredTables>.observe() = plan.observe()

/**
 * Create a [Single] that when subscribed to executes this query against a database and emits the [Cursor]
 * to downstream only once. Subscribers are responsible for **always** closing the emitted [Cursor].
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to
 * [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn], so calling `subscribeOn` has no effect.
 *
 * @return Deferred [Single] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawCursor")
fun CompiledRawCursor<UndeclaredTables>.observe(): Single<Cursor> = plan
  .observe()
  .runQueryOnce()
  .toSingle()
