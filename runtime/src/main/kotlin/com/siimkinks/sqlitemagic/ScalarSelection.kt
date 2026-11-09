package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Selection of single column.
 *
 * @param R Kotlin type of the selected expression values, regardless of `mapTo` and `readAs`
 * @param N Selected expression nullability -- either [Required] or [SqlNullable]
 * @param T Row type
 */
class ScalarSelection<R, N : SqlNullability, T> internal constructor(
  internal val expression: SqlExpression<R, N, *>,
  private val read: ValueRead<T>,
  private val widenedFrom: SqlExpression<*, *, *>? = null,
  override val mappedWithFunction: Boolean = false
) : Selection<T>() {
  override fun render(context: SqlRenderingContext) = context.renderOutput(expression)

  override fun createExecutionReader(context: SqlRenderingContext) = ExecutionReader { read }

  override fun forEachField(action: (ExpressionNode.Field) -> Unit) = expression.node.forEachField(action)

  override val exportedValues get() = listOf(expression)

  override fun widenedIndex(selected: SqlExpression<*, *, *>) = when {
    widenedFrom === selected -> 0
    else -> -1
  }

  internal fun <M> mapped(constructor: (T) -> M) = ScalarSelection(
    expression = expression,
    read = { statement, index -> constructor(read(statement, index)) },
    widenedFrom = widenedFrom,
    mappedWithFunction = true
  )

  internal fun nullable() = ScalarSelection(
    expression = expression.nullable(),
    read = contextualRead(read),
    widenedFrom = expression,
    mappedWithFunction = mappedWithFunction
  )

  internal fun <V> withReader(read: ValueRead<V>) = ScalarSelection(
    expression = expression,
    read = read,
    widenedFrom = widenedFrom
  )
}
