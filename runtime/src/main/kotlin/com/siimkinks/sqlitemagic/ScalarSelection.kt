package com.siimkinks.sqlitemagic

import android.database.Cursor
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
  private val read: (Cursor, Int) -> T
) : Selection<T>() {
  override fun render(context: SqlRenderingContext) = context.renderOutput(expression)

  override fun createExecutionReader(context: SqlRenderingContext) = ExecutionReader { read }

  override fun forEachField(action: (ExpressionNode.Field) -> Unit) = expression.node.forEachField(action)

  override val exportedValues get() = listOf(expression)

  internal fun <M> mapped(constructor: (T) -> M) = withReader { cursor, index -> constructor(read(cursor, index)) }

  internal fun nullable() = ScalarSelection(
    expression = expression.nullable(),
    read = contextualRead(read)
  )

  internal fun <V> withReader(read: (Cursor, Int) -> V) = ScalarSelection(
    expression = expression,
    read = read
  )
}
