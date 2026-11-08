@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.SelectNode
import com.siimkinks.sqlitemagic.internal.sql.compileCount
import com.siimkinks.sqlitemagic.internal.sql.compileFirst
import com.siimkinks.sqlitemagic.internal.sql.compileSelect

/**
 * SQL SELECT statement.
 *
 * Execute it with [execute], observe it with a reactive adapter's `observe()`, or compile it once with [compile],
 * [takeFirst] or [count] to execute it many times. Queries are immutable and can be reused. A query can also be
 * used as a subquery and as a source in FROM and JOIN clauses.
 *
 * @param T Selection return type
 * @param S Selection type
 */
open class SelectQuery<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : QueryOutputSource<T>(), ClauseHost<T, S>, SqlOperand {
  final override val handle = QueryHandle(initialNode)
  internal val node get() = handle.node

  override val occurrence
    get() = DerivedTable(
      query = this,
      alias = null
    )

  /**
   * Create an alias for this query, which can then be used as a subquery in FROM and JOIN clauses.
   *
   * Example:
   * ```
   * val books = SELECT
   *     .COLUMNS(BOOK.ID, BOOK.TITLE)
   *     .FROM(BOOK)
   * val published = books AS "published"
   * SELECT COLUMN published[BOOK.TITLE] FROM published
   * ```
   *
   * @param alias The alias name
   * @return Aliased subquery
   */
  @CheckResult
  infix fun AS(alias: String) = DerivedSource(
    DerivedTable(
      query = this,
      alias = alias
    )
  )

  /**
   * Convert this query into a common table expression, which can then be declared with [WITH].
   *
   * The common table expression is named automatically. Use `(query AS "name").asCte()` to name it.
   *
   * @return Common table expression
   */
  fun asCte() = cte()

  override fun cte() = declared(
    query = this,
    name = null
  )

  internal fun nestedNode() = node.also {
    require(it.connection == null) {
      "A nested query cannot use a connection; choose it on the outermost query"
    }
  }

  /**
   * Configure this query to use provided connection for database operation.
   *
   * A query that uses a connection cannot be used as a subquery, a common table expression or a part of a compound
   * SELECT; configure the connection of the outermost query.
   *
   * @param connection Database connection
   * @return SQL SELECT statement
   */
  fun usingConnection(connection: DbConnection) = SelectQuery(
    node.copy(connection = connection as DbConnectionImpl)
  )

  /**
   * Compile select builder.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled select statement
   */
  fun compile() = compileSelect(node)

  /**
   * Compile and execute this select statement against a database.
   *
   * Returned value will never be `null`. If query returns no rows then resulting list will be empty.
   * This method runs synchronously in the calling thread.
   *
   * @return Query result
   * @throws IllegalArgumentException if the query has parameters. Use [compile] and bind their values instead
   */
  fun execute() = compile().execute()

  /**
   * Compile and execute this select statement against a database and read its rows inside [block].
   *
   * The rows are read lazily, one at a time, while [block] iterates them. They can be iterated once and only inside
   * [block], on the calling thread. The query holds a database connection until [block] returns, so keep [block]
   * short. This method runs synchronously in the calling thread.
   *
   * Example:
   * ```
   * val firstBooks = (SELECT FROM BOOK ORDER_BY BOOK.TITLE.ASC())
   *     .useRows { books -> books.take(10).toList() }
   * ```
   *
   * @param block Reads the rows of the query
   * @return Result of [block]
   * @throws IllegalArgumentException if the query has parameters. Use [compile] and bind their values instead
   */
  fun <R> useRows(block: (Sequence<T>) -> R): R = compile().useRows(block)

  /**
   * Compile select builder and instruct it to take only the first element from the result set.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * This method adds "LIMIT 1" clause to the underlying SQL, unless the query already has a LIMIT clause.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled select statement
   */
  fun takeFirst() = compileFirst(node)

  /**
   * Compile select builder and instruct it to count only the rows of the resulting query.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * Resulting SQL will be "SELECT count(*) FROM (SELECT ...)", or "SELECT count(*) FROM ..." when the query selects all
   * columns of a table or view without DISTINCT, GROUP BY, HAVING, a compound, ORDER BY, LIMIT or OFFSET.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled select statement
   */
  fun count() = compileCount(node)
}
