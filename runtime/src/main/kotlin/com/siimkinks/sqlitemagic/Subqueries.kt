package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * Convert this single column query into an expression, which can be used as a scalar subquery.
 *
 * SQL: (SELECT...)
 *
 * The result is NULL when the query returns no rows. When it returns several rows then the first one is used.
 * Values are read as the selected column, regardless of `mapTo` or `readAs` of this query. Columns of tables that
 * this query does not select from refer to the enclosing query.
 *
 * Example:
 * ```
 * val latestId = SELECT
 *     .COLUMN(MAX(BOOK.ID))
 *     .FROM(BOOK)
 * SELECT FROM BOOK WHERE (BOOK.ID IS latestId.asScalar())
 * ```
 *
 * @return Expression representing the result of this subquery
 */
fun <R, N : SqlNullability, T> SelectQuery<T, ScalarSelection<R, N, T>>.asScalar() =
  node.selection.expression.nonNullRead.let { read ->
    SqlExpression(
      node = ExpressionNode.Subquery(nestedNode()),
      nonNullRead = read,
      selectRead = contextualRead(read),
      nullness = SqlNullable
    )
  }
