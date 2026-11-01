package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.QueryObservation
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.PreparedQuery

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

  internal abstract fun mapRows(statement: SQLiteStatement, isDisposed: () -> Boolean): T

  internal fun boundArguments() = arguments ?: prepared.bind(emptyList())

  internal fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean) = prepared.withStatement(
    dbConnection = dbConnection,
    values = boundArguments()
  ) { statement ->
    mapRows(
      statement = statement,
      isDisposed = isDisposed
    )
  }

  internal fun executePlan() = useStatement { statement ->
    mapRows(
      statement = statement,
      isDisposed = NOT_DISPOSED
    )
  }

  internal inline fun <R> useStatement(block: (SQLiteStatement) -> R) = prepared.withStatement(
    values = boundArguments(),
    dbConnection = prepared.connection.orDefault(),
    block = block
  )

  internal fun observation(): QueryObservation<T> {
    boundArguments()
    return QueryObservation(this)
  }
}

private class PlanExecution<T>(
  private val plan: SelectPlan<T>
) : Query<T>(plan.prepared.connection) {
  override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean) = plan.run(
    dbConnection = dbConnection,
    isDisposed = isDisposed
  )
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
   * Execute this compiled select statement against a database and read its rows inside [block].
   *
   * The rows are read lazily, one at a time, while [block] iterates them. They can be iterated once and only inside
   * [block], on the calling thread. The query holds a database connection until [block] returns, so keep [block]
   * short. This method runs synchronously in the calling thread.
   *
   * Example:
   * ```
   * val firstTitles = (SELECT COLUMN BOOK.TITLE FROM BOOK ORDER_BY BOOK.TITLE.ASC())
   *     .compile()
   *     .useRows { titles -> titles.take(10).toList() }
   * ```
   *
   * @param block Reads the rows of the query
   * @return Result of [block]
   * @throws IllegalArgumentException if the query has parameters without bound values
   */
  fun <R> useRows(block: (Sequence<T>) -> R): R = useStatement { statement ->
    QueryRows(
      statement = statement,
      read = reader.open()
    ).use(block)
  }

  override fun mapRows(statement: SQLiteStatement, isDisposed: () -> Boolean): List<T> {
    val read = reader.open()
    return buildList {
      while (!isDisposed() && statement.step()) {
        add(read(statement, 0))
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

  override fun mapRows(statement: SQLiteStatement, isDisposed: () -> Boolean): T? = when {
    statement.step() -> reader.open()(statement, 0)
    else -> null
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

  override fun mapRows(statement: SQLiteStatement, isDisposed: () -> Boolean): Long {
    statement.step()
    return statement.getLong(0)
  }
}
