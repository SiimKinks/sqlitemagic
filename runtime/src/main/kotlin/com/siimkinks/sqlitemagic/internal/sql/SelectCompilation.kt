package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledCursor
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.ExecutionReader

internal fun <T> compileSelect(node: SelectNode<T, *>) = node.compile(::CompiledSelect)

internal fun <T> compileFirst(node: SelectNode<T, *>) = when (node.limit) {
  null -> node.copy(limit = 1L)
  else -> node
}.compile(::CompiledFirst)

internal fun <T> compileCursor(node: SelectNode<T, *>) = node.compile(::CompiledCursor)

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
    context.sql.append("SELECT count(*) FROM (")
    context.renderNested { select.render(context) }
    context.sql.append(')')
  }
}
