package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException

/**
 * Compiled raw SQL query.
 *
 * This object is immutable and can be shared across multiple threads without side effects.
 *
 * @param T Row type
 * @param O Whether the query defines the tables it reads -- either [DeclaredTables] or [UndeclaredTables]
 */
class CompiledRawSelect<T, O : RawTables> internal constructor(
  internal val plan: CompiledSelect<T>
) {
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
class CompiledRawFirst<T, O : RawTables> internal constructor(
  internal val plan: CompiledFirst<T>
) {
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
open class CompiledRawCursor<O : RawTables> internal constructor(
  internal val plan: CompiledCursor<*>
) {
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
