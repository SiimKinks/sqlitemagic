package com.siimkinks.sqlitemagic.internal.sql

import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.ConflictAlgorithm.Companion.CONFLICT_VALUES
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.requireHostValue

internal class InsertHead(
  override val table: Table<*>,
  private val conflictAlgorithm: Int,
  private val columns: Array<out Column<*, *, *, *, *, *, *>>,
  private val rows: List<InsertRows>
) : MutationHead {
  override val deletedTable
    get() = when (conflictAlgorithm) {
      CONFLICT_REPLACE -> table.name
      else -> null
    }

  override val cache get() = rows.none { it is ListedRows<*> }

  override val skipped get() = columns.isNotEmpty() && rows.all { it.count == 0 }

  override fun render(context: SqlRenderingContext) {
    context.sql
      .append("INSERT")
      .append(CONFLICT_VALUES[conflictAlgorithm])
      .append(" INTO ")
      .append(table.sqlSource)
    when {
      columns.isEmpty() -> context.sql.append(" DEFAULT VALUES")
      else -> {
        context.sql.append(" (")
        context.renderList(columns.size) { context.sql.append(columns[it].quotedName) }
        context.sql.append(')')
        renderRows(context)
      }
    }
  }

  private fun renderRows(context: SqlRenderingContext) {
    when {
      skipped -> {
        context.sql.append(" SELECT ")
        context.renderList(columns.size) { context.sql.append("NULL") }
        context.sql.append(" WHERE 0")
      }
      else -> {
        context.sql.append(" VALUES ")
        context.insertingValues = true
        var rendered = false
        rows.forEach { row ->
          if (row.count != 0) {
            if (rendered) {
              context.sql.append(", ")
            }
            row.render(context)
            rendered = true
          }
        }
        context.insertingValues = false
      }
    }
  }

  fun withColumns(columns: Array<out Column<*, *, *, *, *, *, *>>) = InsertHead(
    table = table,
    conflictAlgorithm = conflictAlgorithm,
    columns = columns.onEach { column ->
      require(column.table === table) {
        "INSERT column ${column.name} must be a field of the inserted ${table.name} occurrence"
      }
    },
    rows = rows
  )

  fun withRows(added: InsertRows) = InsertHead(
    table = table,
    conflictAlgorithm = conflictAlgorithm,
    columns = columns,
    rows = rows + added
  )

  fun hostRow(values: Array<out Any?>) = ValuesRow(
    List(values.size) { index -> ExpressionNode.Bound(columns[index].encodeHost(values[index])) }
  )

  fun <T> listedRows(rows: List<T>, component: (T, Int) -> Any?) = ListedRows(
    rows = rows,
    columns = columns,
    component = component
  )
}

internal sealed interface InsertRows : SqlRenderable {
  val count: Int
}

internal class ValuesRow(private val values: List<ExpressionNode>) : InsertRows {
  override val count get() = 1

  override fun render(context: SqlRenderingContext) {
    context.sql.append('(')
    context.renderList(values) { it.render(context) }
    context.sql.append(')')
  }
}

internal class ListedRows<T>(
  private val rows: List<T>,
  private val columns: Array<out Column<*, *, *, *, *, *, *>>,
  private val component: (T, Int) -> Any?
) : InsertRows {
  override val count get() = rows.size

  override fun render(context: SqlRenderingContext) = context.renderList(rows) { row ->
    context.sql.append('(')
    context.renderList(columns.size) { index -> context.bind(columns[index].encodeHost(component(row, index))) }
    context.sql.append(')')
  }
}

internal fun MutationNode<InsertHead>.withColumns(vararg columns: Column<*, *, *, *, *, *, *>) =
  copy(head = head.withColumns(columns))

internal fun MutationNode<InsertHead>.withHostRow(vararg values: Any?) =
  copy(head = head.withRows(head.hostRow(values)))

internal fun MutationNode<InsertHead>.withExpressionRow(vararg values: SqlExpression<*, *, *>) =
  copy(head = head.withRows(ValuesRow(values.map(SqlExpression<*, *, *>::node))))

internal fun <T> MutationNode<InsertHead>.withListedRows(rows: List<T>, component: (T, Int) -> Any?) = copy(
  head = head.withRows(
    head.listedRows(
      rows = rows,
      component = component
    )
  )
)

@Suppress("UNCHECKED_CAST")
private fun Column<*, *, *, *, *, *, *>.encodeHost(value: Any?): NativeValue {
  requireHostValue(value)
  return (encode as (Any?) -> NativeValue)(value)
}
