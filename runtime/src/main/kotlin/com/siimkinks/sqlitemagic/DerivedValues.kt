package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * Column selected by a subquery or a common table expression, referenced from the outer query.
 *
 * Create one with `source[column]`, for example `published[AUTHOR.NAME]`. It is read and compared with values like
 * the selected column.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class DerivedField<W, R, N : SqlNullability, T> internal constructor(
  node: ExpressionNode,
  selected: EncodableSqlExpression<W, R, N, T>
) : EncodableSqlExpression<W, R, N, T>(
  source = selected,
  node = node,
  outputAlias = null
)

/**
 * Expression selected by a subquery or a common table expression, referenced from the outer query.
 *
 * Create one with `source[expression]`. It is read like the selected expression.
 *
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class DerivedValue<R, N : SqlNullability, T> internal constructor(
  node: ExpressionNode,
  selected: SqlExpression<R, N, T>
) : SqlExpression<R, N, T>(
  source = selected,
  node = node,
  outputAlias = null
)
