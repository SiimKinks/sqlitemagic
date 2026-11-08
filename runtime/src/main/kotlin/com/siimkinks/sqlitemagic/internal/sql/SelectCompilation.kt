package com.siimkinks.sqlitemagic.internal.sql

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.EntitySelection
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.Selection

internal fun <T> compileSelect(node: SelectNode<T, *>) = node.compile(::CompiledSelect)

internal fun <T> compileFirst(node: SelectNode<T, *>) = when (node.limit) {
  null -> node.copy(limit = 1L)
  else -> node
}.compile(::CompiledFirst)

internal fun compileCount(node: SelectNode<*, *>) = CompiledCount(
  prepareQuery(
    renderable = CountNode(node),
    connection = node.connection
  )
)

private inline fun <T, P> SelectNode<T, *>.compile(plan: (PreparedQuery, ExecutionReader<T>) -> P): P {
  val context = SqlRenderingContext()
  val prepared = prepareQuery(
    renderable = this,
    connection = connection,
    context = context
  )
  return plan(prepared, selection.createExecutionReader(context))
}

private class CountNode(private val select: SelectNode<*, *>) : SqlRenderable {
  override fun render(context: SqlRenderingContext) {
    when {
      select.countsRows -> select
          .withSelection(CountRows)
          .render(context)
      else -> {
        context.sql.append("SELECT count(*) FROM (")
        context.renderNested { select.render(context) }
        context.sql.append(')')
      }
    }
  }
}

private val SelectNode<*, *>.countsRows
  get() = selection is EntitySelection<*> &&
      !distinct &&
      compound == null &&
      groupings.isEmpty() &&
      having == null &&
      orderings.isEmpty() &&
      limit == null &&
      offset == null

private object CountRows : Selection<Long>() {
  override fun render(context: SqlRenderingContext) {
    context.sql.append("count(*)")
  }

  override fun createExecutionReader(context: SqlRenderingContext) = ExecutionReader { SQLiteStatement::getLong }
}
