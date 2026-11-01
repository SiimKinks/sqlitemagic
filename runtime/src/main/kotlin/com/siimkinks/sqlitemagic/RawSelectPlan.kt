package com.siimkinks.sqlitemagic

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

  /**
   * Execute this compiled query against a database and read its rows inside [block].
   *
   * The rows are read lazily, one at a time, while [block] iterates them. They can be iterated once and only inside
   * [block], on the calling thread. The query holds a database connection until [block] returns, so keep [block]
   * short. This method runs synchronously in the calling thread.
   *
   * @param block Reads the rows of the query
   * @return Result of [block]
   * @throws IllegalStateException if the query returns another number of columns than `readAs` reads
   * @throws SQLException if a column read with a not nullable type holds SQL NULL
   */
  fun <R> useRows(block: (Sequence<T>) -> R): R = plan.useRows(block)
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
