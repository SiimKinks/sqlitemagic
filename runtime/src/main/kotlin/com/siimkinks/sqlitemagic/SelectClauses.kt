@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.CROSS
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.FULL
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.INNER
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.LEFT
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.NATURAL
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.NATURAL_CROSS
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.NATURAL_FULL
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.NATURAL_LEFT
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.NATURAL_RIGHT
import com.siimkinks.sqlitemagic.internal.sql.JoinKind.RIGHT
import com.siimkinks.sqlitemagic.internal.sql.SelectNode

/**
 * Internal state of SQL SELECT statement builder.
 */
class QueryHandle<T, S : Selection<T>> internal constructor(internal val node: SelectNode<T, S>)

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
sealed interface ClauseHost<T, S : Selection<T>> {
  val handle: QueryHandle<T, S>
}

/**
 * Builder for SQL SELECT statement with JOIN clauses.
 *
 * @param T Selection return type
 * @param S Selection type
 */
interface JoinClause<T, S : Selection<T>> : ClauseHost<T, S> {
  /**
   * INNER JOIN a table or subquery to the query.
   *
   * Follow it with `ON` or `USING` to define the join constraint.
   *
   * Example:
   * ```
   * SELECT FROM BOOK INNER_JOIN AUTHOR ON (BOOK.AUTHOR IS AUTHOR.ID)
   * ```
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun INNER_JOIN(source: Source) = SelectJoinStage(
    handle.node.withJoin(
      kind = INNER,
      source = source
    )
  )

  /**
   * LEFT JOIN a table or subquery to the query.
   *
   * Follow it with `ON` or `USING` to define the join constraint.
   *
   * Columns of the joined source are NULL in rows that have no match, so select its not nullable columns
   * with `nullable()`.
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(AUTHOR.NAME, BOOK.TITLE.nullable())
   *     .FROM(AUTHOR)
   *     .LEFT_JOIN(BOOK)
   *     .ON(AUTHOR.ID IS BOOK.AUTHOR)
   * ```
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun LEFT_JOIN(source: Source) = SelectJoinStage(
    handle.node.withJoin(
      kind = LEFT,
      source = source
    )
  )

  /**
   * RIGHT JOIN a table or subquery to the query.
   *
   * Follow it with `ON` or `USING` to define the join constraint.
   *
   * Columns of the sources before the joined one are NULL in rows that have no match, so select their not
   * nullable columns with `nullable()`. Requires SQLite 3.39.0 or newer.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun RIGHT_JOIN(source: Source) = SelectJoinStage(
    handle.node.withJoin(
      kind = RIGHT,
      source = source
    )
  )

  /**
   * FULL JOIN a table or subquery to the query.
   *
   * Follow it with `ON` or `USING` to define the join constraint.
   *
   * Columns of all joined sources are NULL in rows that have no match, so select their not nullable columns
   * with `nullable()`. Requires SQLite 3.39.0 or newer.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun FULL_JOIN(source: Source) = SelectJoinStage(
    handle.node.withJoin(
      kind = FULL,
      source = source
    )
  )

  /**
   * CROSS JOIN a table or subquery to the query.
   *
   * Follow it with `ON` or `USING` to define the join constraint. SQLite does not reorder CROSS JOIN sources
   * when it optimizes the query.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun CROSS_JOIN(source: Source) = SelectJoinStage(
    handle.node.withJoin(
      kind = CROSS,
      source = source
    )
  )

  /**
   * NATURAL JOIN a table or subquery to the query.
   *
   * If the NATURAL keyword is in the join-operator then an implicit USING clause
   * is added to the join-constraints. The implicit USING clause contains each of
   * the column names that appear in both the left and right-hand input datasets.
   * If the left and right-hand input datasets feature no common column names, then
   * the NATURAL keyword has no effect on the results of the join.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun NATURAL_JOIN(source: Source) = SelectFromStage(
    handle.node.withJoin(
      kind = NATURAL,
      source = source
    )
  )

  /**
   * NATURAL LEFT JOIN a table or subquery to the query.
   *
   * If the NATURAL keyword is in the join-operator then an implicit USING clause
   * is added to the join-constraints. The implicit USING clause contains each of
   * the column names that appear in both the left and right-hand input datasets.
   * If the left and right-hand input datasets feature no common column names, then
   * the NATURAL keyword has no effect on the results of the join.
   *
   * Columns of the joined source are NULL in rows that have no match, so select its not nullable columns
   * with `nullable()`.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun NATURAL_LEFT_JOIN(source: Source) = SelectFromStage(
    handle.node.withJoin(
      kind = NATURAL_LEFT,
      source = source
    )
  )

  /**
   * NATURAL RIGHT JOIN a table or subquery to the query.
   *
   * If the NATURAL keyword is in the join-operator then an implicit USING clause
   * is added to the join-constraints. The implicit USING clause contains each of
   * the column names that appear in both the left and right-hand input datasets.
   * If the left and right-hand input datasets feature no common column names, then
   * the NATURAL keyword has no effect on the results of the join.
   *
   * Columns of the sources before the joined one are NULL in rows that have no match, so select their not
   * nullable columns with `nullable()`. Requires SQLite 3.39.0 or newer.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun NATURAL_RIGHT_JOIN(source: Source) = SelectFromStage(
    handle.node.withJoin(
      kind = NATURAL_RIGHT,
      source = source
    )
  )

  /**
   * NATURAL FULL JOIN a table or subquery to the query.
   *
   * If the NATURAL keyword is in the join-operator then an implicit USING clause
   * is added to the join-constraints. The implicit USING clause contains each of
   * the column names that appear in both the left and right-hand input datasets.
   * If the left and right-hand input datasets feature no common column names, then
   * the NATURAL keyword has no effect on the results of the join.
   *
   * Columns of all joined sources are NULL in rows that have no match, so select their not nullable columns
   * with `nullable()`. Requires SQLite 3.39.0 or newer.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun NATURAL_FULL_JOIN(source: Source) = SelectFromStage(
    handle.node.withJoin(
      kind = NATURAL_FULL,
      source = source
    )
  )

  /**
   * NATURAL CROSS JOIN a table or subquery to the query.
   *
   * If the NATURAL keyword is in the join-operator then an implicit USING clause
   * is added to the join-constraints. The implicit USING clause contains each of
   * the column names that appear in both the left and right-hand input datasets.
   * If the left and right-hand input datasets feature no common column names, then
   * the NATURAL keyword has no effect on the results of the join.
   *
   * @param source Table, subquery or common table expression to join
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun NATURAL_CROSS_JOIN(source: Source) = SelectFromStage(
    handle.node.withJoin(
      kind = NATURAL_CROSS,
      source = source
    )
  )
}

/**
 * Builder for SQL SELECT statement with WHERE clause.
 *
 * @param T Selection return type
 * @param S Selection type
 */
interface WhereClause<T, S : Selection<T>> : ClauseHost<T, S> {
  /**
   * Define a WHERE clause.
   *
   * @param predicate WHERE clause expression
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun WHERE(predicate: SqlExpression<Boolean, *, *>) = SelectWhereStage(
    handle.node.copy(where = predicate.node)
  )
}

/**
 * Builder for SQL SELECT statement with GROUP BY clause.
 *
 * @param T Selection return type
 * @param S Selection type
 */
interface GroupClause<T, S : Selection<T>> : ClauseHost<T, S> {
  /**
   * Add a GROUP BY clause to the query.
   *
   * @param expression Expression to group selection by
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun GROUP_BY(expression: SqlExpression<*, *, *>) = SelectGroupStage(
    handle.node.copy(groupings = listOf(expression.node))
  )

  /**
   * Add a GROUP BY clause to the query.
   *
   * Example:
   * ```
   * SELECT COLUMN COUNT() FROM BOOK GROUP_BY arrayOf(BOOK.AUTHOR, BOOK.YEAR)
   * ```
   *
   * @param expressions Expressions to group selection by
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun GROUP_BY(expressions: Array<out SqlExpression<*, *, *>>) = SelectGroupStage(
    handle.node.copy(groupings = expressions.map(SqlExpression<*, *, *>::node))
  )

  /**
   * Add a GROUP BY clause to the query.
   *
   * @param first The first expression to group selection by
   * @param second The second expression to group selection by
   * @param additional Additional expressions to group selection by
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun GROUP_BY(
    first: SqlExpression<*, *, *>,
    second: SqlExpression<*, *, *>,
    vararg additional: SqlExpression<*, *, *>
  ) = SelectGroupStage(
    handle.node.copy(
      groupings = buildList(additional.size + 2) {
        add(first.node)
        add(second.node)
        additional.mapTo(
          destination = this,
          transform = SqlExpression<*, *, *>::node
        )
      }
    )
  )
}

/**
 * Builder for SQL SELECT statement with ORDER BY clause.
 *
 * @param T Selection return type
 * @param S Selection type
 */
interface OrderClause<T, S : Selection<T>> : ClauseHost<T, S> {
  /**
   * Add an ORDER BY clause to the query.
   *
   * @param ordering Ordering term that defines ORDER BY clause
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun ORDER_BY(ordering: Ordering) = SelectOrderStage(handle.node.copy(orderings = listOf(ordering)))

  /**
   * Add an ORDER BY clause to the query.
   *
   * Example:
   * ```
   * SELECT FROM BOOK ORDER_BY arrayOf(BOOK.YEAR.DESC(), BOOK.TITLE.ASC())
   * ```
   *
   * @param orderings Ordering terms that define ORDER BY clause
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun ORDER_BY(orderings: Array<out Ordering>) = SelectOrderStage(
    handle.node.copy(orderings = orderings.toList())
  )

  /**
   * Add an ORDER BY clause to the query.
   *
   * @param first The first ordering term
   * @param second The second ordering term
   * @param additional Additional ordering terms
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun ORDER_BY(first: Ordering, second: Ordering, vararg additional: Ordering) = SelectOrderStage(
    handle.node.copy(orderings = listOf(first, second, *additional))
  )
}

/**
 * Builder for SQL SELECT statement with LIMIT clause.
 *
 * @param T Selection return type
 * @param S Selection type
 */
interface LimitClause<T, S : Selection<T>> : ClauseHost<T, S> {
  /**
   * Add a LIMIT clause to the query.
   *
   * @param value Upper bound on the number of rows returned by the
   *              entire SELECT statement
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun LIMIT(value: Long) = SelectLimitStage(handle.node.copy(limit = value))

  /**
   * Add a LIMIT clause to the query.
   *
   * @param value Upper bound on the number of rows returned by the
   *              entire SELECT statement
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun LIMIT(value: Int) = LIMIT(value.toLong())
}
