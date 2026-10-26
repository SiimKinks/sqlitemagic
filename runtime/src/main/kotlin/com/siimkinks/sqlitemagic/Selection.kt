package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.RelationshipPlanner
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext
import com.siimkinks.sqlitemagic.internal.sql.derivedOutputName
import com.siimkinks.sqlitemagic.internal.sql.fieldOutputName

/**
 * Selection of SQL SELECT statement: the selected columns and how they are read.
 *
 * @param T Row type
 */
abstract class Selection<out T> internal constructor() {
  internal abstract fun render(context: SqlRenderingContext)

  internal abstract fun createExecutionReader(context: SqlRenderingContext): ExecutionReader<T>

  internal open fun forEachField(action: (ExpressionNode.Field) -> Unit) = Unit

  internal open fun planRelatedEntities(planner: RelationshipPlanner): RelatedEntities? = null

  internal open val exportedValues: List<SqlExpression<*, *, *>> get() = emptyList()

  internal open fun exportedName(index: Int) = derivedOutputName(
    expression = exportedValues[index],
    index = index
  )
}

internal fun SqlRenderingContext.renderOutput(expression: SqlExpression<*, *, *>) {
  if (expression.nullness == Required && !nested) {
    expression.node
      .outerJoinNull(this)
      ?.let { field ->
        val output = fieldOutputName(
          table = field.table,
          name = field.name
        )
        throw IllegalArgumentException(
          "$output is NOT NULL but an outer join can leave it NULL; select the field with .nullable()"
        )
      }
  }
  appendOutput(expression)
}

internal inline fun SqlRenderingContext.requireNotNullExtended(source: Table<*>, message: () -> String) = require(
  value = nested || !isNullExtended(source),
  lazyMessage = message
)

internal fun SqlRenderingContext.requireEntityNotNullExtended(table: Table<*>) = requireNotNullExtended(table) {
  "An outer join can leave the ${table.referenceName} entity NULL; select its fields with .nullable()"
}

internal fun SqlRenderingContext.appendOutput(expression: SqlExpression<*, *, *>) {
  expression.node.render(this)
  nameOutput(expression)
}

internal fun interface ExecutionReader<out T> {
  fun open(): (Cursor, Int) -> T
}
