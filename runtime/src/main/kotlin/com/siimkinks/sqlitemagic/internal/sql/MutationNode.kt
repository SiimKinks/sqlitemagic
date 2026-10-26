package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.CompiledMutation
import com.siimkinks.sqlitemagic.ConflictAlgorithm
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.MutationStatement
import com.siimkinks.sqlitemagic.ParameterAssignment
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.Table

internal data class MutationNode<H : MutationHead>(
  val head: H,
  val where: ExpressionNode? = null,
  val connection: DbConnectionImpl? = null,
  val parameterValues: List<ParameterAssignment> = emptyList()
) : SqlRenderable {
  override fun render(context: SqlRenderingContext) {
    context.addSource(head.table)
    head.render(context)
    if (where != null) {
      context.sql.append(" WHERE ")
      where.render(context)
    }
  }

  fun filtered(predicate: SqlExpression<Boolean, *, *>): MutationQuery = MutationStatement(copy(where = predicate.node))

  companion object {
    fun update(table: Table<*>, conflictAlgorithm: Int) = MutationNode(
      head = UpdateHead(
        table = mutationTarget(table),
        conflictAlgorithm = conflictAlgorithm,
        assignments = emptyList()
      )
    )

    fun delete(table: Table<*>) = MutationNode(head = DeleteHead(mutationTarget(table)))

    private fun mutationTarget(table: Table<*>) = table.also {
      require(it.alias == null) {
        "UPDATE and DELETE targets cannot be aliased; use ${it.name} itself and alias inner queries instead"
      }
    }
  }
}

internal fun MutationNode<UpdateHead>.withAssignment(
  column: Column<*, *, *, *, *, *, *>,
  value: ExpressionNode
): MutationNode<UpdateHead> {
  require(column.table === head.table) {
    "SET target ${column.name} must be a field of the updated ${head.table.name} occurrence"
  }
  return copy(
    head = head.with(
      ColumnAssignment(
        column = column,
        value = value
      )
    )
  )
}

internal sealed interface MutationHead : SqlRenderable {
  val table: Table<*>
}

internal class UpdateHead(
  override val table: Table<*>,
  val conflictAlgorithm: Int,
  val assignments: List<ColumnAssignment>
) : MutationHead {
  override fun render(context: SqlRenderingContext) {
    context.sql
      .append("UPDATE")
      .append(ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm])
      .append(' ')
      .append(table.sqlSource)
      .append(" SET ")
    context.renderList(assignments) { assignment ->
      context.sql
        .append(assignment.quotedName)
        .append(" = ")
      assignment.value.render(context)
    }
  }

  fun with(assignment: ColumnAssignment) = UpdateHead(
    table = table,
    conflictAlgorithm = conflictAlgorithm,
    assignments = assignments + assignment
  )
}

internal class DeleteHead(override val table: Table<*>) : MutationHead {
  override fun render(context: SqlRenderingContext) {
    context.sql
      .append("DELETE FROM ")
      .append(table.sqlSource)
  }
}

internal class ColumnAssignment(
  column: Column<*, *, *, *, *, *, *>,
  val value: ExpressionNode
) {
  val quotedName = (column.node as ExpressionNode.Field).quotedName
}

internal fun compileMutation(
  renderable: SqlRenderable,
  connection: DbConnectionImpl?,
  tables: Array<out String>,
  parameterValues: List<ParameterAssignment> = emptyList()
) = prepareQuery(
  renderable = renderable,
  connection = connection
)
  .let { prepared ->
    CompiledMutation(
      prepared = prepared,
      arguments = prepared.arguments(parameterValues),
      tables = tables
    )
  }
