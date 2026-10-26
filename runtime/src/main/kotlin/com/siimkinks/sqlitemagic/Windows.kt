@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Window definition of a window function.
 *
 * Create one with [PARTITION_BY], [ORDER_BY] or [ROWS_BETWEEN]. Window functions require SQLite 3.25.0 or newer.
 *
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
open class WindowSpec internal constructor(
  internal val partitions: List<ExpressionNode>,
  internal val orderings: List<Ordering>,
  private val frame: WindowFrame?
) {
  internal fun render(context: SqlRenderingContext) {
    context.sql.append('(')
    var separator = ""
    if (partitions.isNotEmpty()) {
      context.sql.append("PARTITION BY ")
      context.renderList(partitions) { it.render(context) }
      separator = " "
    }
    if (orderings.isNotEmpty()) {
      context.sql
        .append(separator)
        .append("ORDER BY ")
      context.renderList(orderings) { it.render(context) }
      separator = " "
    }
    if (frame != null) {
      context.sql
        .append(separator)
        .append("ROWS BETWEEN ")
      frame.start.render(context)
      context.sql.append(" AND ")
      frame.end.render(context)
    }
    context.sql.append(')')
  }

  internal fun forEachField(action: (ExpressionNode.Field) -> Unit) {
    partitions.forEach { it.forEachField(action) }
    orderings.forEach { it.node.forEachField(action) }
    frame?.start?.offset?.forEachField(action)
    frame?.end?.offset?.forEachField(action)
  }
}

/**
 * Window definition with a PARTITION BY clause.
 */
class PartitionedWindowSpec internal constructor(partitions: List<ExpressionNode>) : WindowSpec(
  partitions = partitions,
  orderings = emptyList(),
  frame = null
) {
  /**
   * Add an ORDER BY clause to this window definition.
   *
   * @param ordering Ordering term
   * @return Window definition
   */
  infix fun ORDER_BY(ordering: Ordering) = OrderedWindowSpec(
    partitions = partitions,
    orderings = listOf(ordering)
  )

  /**
   * Add an ORDER BY clause to this window definition.
   *
   * @param first The first ordering term
   * @param second The second ordering term
   * @param additional Additional ordering terms
   * @return Window definition
   */
  fun ORDER_BY(first: Ordering, second: Ordering, vararg additional: Ordering) = OrderedWindowSpec(
    partitions = partitions,
    orderings = orderings(
      first = first,
      second = second,
      additional = additional
    )
  )

  /**
   * Add a ROWS frame to this window definition.
   *
   * Example:
   * ```
   * PARTITION_BY(BOOK.AUTHOR)
   *     .ROWS_BETWEEN(UNBOUNDED_PRECEDING)
   *     .AND(CURRENT_ROW)
   * ```
   *
   * @param start The frame start boundary
   * @return Window frame builder
   */
  infix fun ROWS_BETWEEN(start: FrameBound) = WindowFrameStart(
    partitions = partitions,
    orderings = emptyList(),
    start = start
  )
}

/**
 * Window definition with an ORDER BY clause.
 */
class OrderedWindowSpec internal constructor(
  partitions: List<ExpressionNode>,
  orderings: List<Ordering>
) : WindowSpec(
  partitions = partitions,
  orderings = orderings,
  frame = null
) {
  /**
   * Add a ROWS frame to this window definition.
   *
   * Example:
   * ```
   * ORDER_BY(BOOK.ID.ASC())
   *     .ROWS_BETWEEN(2.PRECEDING)
   *     .AND(CURRENT_ROW)
   * ```
   *
   * @param start The frame start boundary
   * @return Window frame builder
   */
  infix fun ROWS_BETWEEN(start: FrameBound) = WindowFrameStart(
    partitions = partitions,
    orderings = orderings,
    start = start
  )
}

/**
 * Builder for ROWS frame of a window definition.
 */
class WindowFrameStart internal constructor(
  private val partitions: List<ExpressionNode>,
  private val orderings: List<Ordering>,
  private val start: FrameBound
) {
  /**
   * Complete the ROWS frame.
   *
   * @param end The frame end boundary
   * @return Window definition
   */
  infix fun AND(end: FrameBound) = WindowSpec(
    partitions = partitions,
    orderings = orderings,
    frame = WindowFrame(
      start = start,
      end = end
    )
  )
}

internal class WindowFrame(
  val start: FrameBound,
  val end: FrameBound
)

/**
 * Boundary of a ROWS frame.
 *
 * Use [CURRENT_ROW], [UNBOUNDED_PRECEDING], [UNBOUNDED_FOLLOWING] or a number of rows, for example `2.PRECEDING`.
 */
class FrameBound internal constructor(
  internal val offset: ExpressionNode?,
  private val sql: String
) {
  internal fun render(context: SqlRenderingContext) {
    if (offset != null) {
      offset.render(context)
      context.sql.append(' ')
    }
    context.sql.append(sql)
  }
}

/**
 * CURRENT ROW frame boundary.
 */
val CURRENT_ROW = FrameBound(
  offset = null,
  sql = "CURRENT ROW"
)

/**
 * UNBOUNDED PRECEDING frame boundary: the first row of the partition.
 */
val UNBOUNDED_PRECEDING = FrameBound(
  offset = null,
  sql = "UNBOUNDED PRECEDING"
)

/**
 * UNBOUNDED FOLLOWING frame boundary: the last row of the partition.
 */
val UNBOUNDED_FOLLOWING = FrameBound(
  offset = null,
  sql = "UNBOUNDED FOLLOWING"
)

/**
 * `n PRECEDING` frame boundary: n rows before the current row.
 */
val Int.PRECEDING
  get() = FrameBound(
    offset = integerValue(toLong()),
    sql = "PRECEDING"
  )

/**
 * `n PRECEDING` frame boundary: n rows before the current row.
 */
val Long.PRECEDING
  get() = FrameBound(
    offset = integerValue(this),
    sql = "PRECEDING"
  )

/**
 * `n PRECEDING` frame boundary: n rows before the current row.
 */
val SqlParameter<*, out Number?, *, *>.PRECEDING
  get() = FrameBound(
    offset = node,
    sql = "PRECEDING"
  )

/**
 * `n FOLLOWING` frame boundary: n rows after the current row.
 */
val Int.FOLLOWING
  get() = FrameBound(
    offset = integerValue(toLong()),
    sql = "FOLLOWING"
  )

/**
 * `n FOLLOWING` frame boundary: n rows after the current row.
 */
val Long.FOLLOWING
  get() = FrameBound(
    offset = integerValue(this),
    sql = "FOLLOWING"
  )

/**
 * `n FOLLOWING` frame boundary: n rows after the current row.
 */
val SqlParameter<*, out Number?, *, *>.FOLLOWING
  get() = FrameBound(
    offset = node,
    sql = "FOLLOWING"
  )

/**
 * Create a window definition with a PARTITION BY clause.
 *
 * Example:
 * ```
 * ROW_NUMBER() OVER PARTITION_BY(BOOK.AUTHOR)
 * ```
 *
 * @param first The first expression to partition rows by
 * @param additional Additional expressions to partition rows by
 * @return Window definition
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun PARTITION_BY(first: SqlExpression<*, *, *>, vararg additional: SqlExpression<*, *, *>) = PartitionedWindowSpec(
  buildList(additional.size + 1) {
    add(first.node)
    additional.mapTo(
      destination = this,
      transform = SqlExpression<*, *, *>::node
    )
  }
)

/**
 * Create a window definition with an ORDER BY clause.
 *
 * Example:
 * ```
 * RANK() OVER ORDER_BY(BOOK.PAGES.DESC())
 * ```
 *
 * @param ordering Ordering term
 * @return Window definition
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun ORDER_BY(ordering: Ordering) = OrderedWindowSpec(
  partitions = emptyList(),
  orderings = listOf(ordering)
)

/**
 * Create a window definition with an ORDER BY clause.
 *
 * @param first The first ordering term
 * @param second The second ordering term
 * @param additional Additional ordering terms
 * @return Window definition
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun ORDER_BY(first: Ordering, second: Ordering, vararg additional: Ordering) = OrderedWindowSpec(
  partitions = emptyList(),
  orderings = orderings(
    first = first,
    second = second,
    additional = additional
  )
)

/**
 * Create a window definition with a ROWS frame.
 *
 * Example:
 * ```
 * COUNT() OVER ROWS_BETWEEN(CURRENT_ROW).AND(2.FOLLOWING)
 * ```
 *
 * @param start The frame start boundary
 * @return Window frame builder
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun ROWS_BETWEEN(start: FrameBound) = WindowFrameStart(
  partitions = emptyList(),
  orderings = emptyList(),
  start = start
)

/**
 * Ranking window function.
 *
 * Complete it with `OVER`. Window functions require SQLite 3.25.0 or newer.
 */
class RankingFunction internal constructor(private val function: String) {
  /**
   * Use this function over a window.
   *
   * @param window The window definition
   * @return Expression representing the result of this function
   * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
   */
  infix fun OVER(window: WindowSpec) = requiredNativeResult(
    node = ExpressionNode.Over(
      function = ExpressionNode.Call(
        function = function,
        arguments = emptyList(),
        requiredBy = -1
      ),
      window = window
    ),
    type = INTEGER
  )

  /**
   * Use this function over all rows.
   *
   * SQL: OVER ()
   *
   * @return Expression representing the result of this function
   * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
   */
  fun OVER() = OVER(emptyWindow)
}

/**
 * The row_number() function returns the number of the row within the current partition. Rows are numbered
 * starting from 1 in the order defined by the ORDER BY clause in the window definition, or in arbitrary order
 * otherwise.
 *
 * @return Ranking window function
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun ROW_NUMBER() = RankingFunction("ROW_NUMBER")

/**
 * The rank() function returns the row_number() of the first peer in each group - the rank of the current row with
 * gaps. If there is no ORDER BY clause, then all rows are considered peers and this function always returns 1.
 *
 * @return Ranking window function
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun RANK() = RankingFunction("RANK")

/**
 * The dense_rank() function returns the number of the current row's peer group within its partition - the rank of
 * the current row without gaps. Rows are numbered starting from 1 in the order defined by the ORDER BY clause in the
 * window definition. If there is no ORDER BY clause, then all rows are considered peers and this function always
 * returns 1.
 *
 * @return Ranking window function
 * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
 */
fun DENSE_RANK() = RankingFunction("DENSE_RANK")

internal val emptyWindow = WindowSpec(
  partitions = emptyList(),
  orderings = emptyList(),
  frame = null
)

private fun orderings(first: Ordering, second: Ordering, additional: Array<out Ordering>) =
  buildList(additional.size + 2) {
    add(first)
    add(second)
    addAll(additional)
  }
