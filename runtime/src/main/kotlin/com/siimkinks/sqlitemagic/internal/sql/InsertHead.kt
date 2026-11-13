package com.siimkinks.sqlitemagic.internal.sql

import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.ConflictAlgorithm.Companion.CONFLICT_VALUES
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.requireHostValue

internal class InsertHead(
  override val table: Table<*>,
  private val conflictAlgorithm: Int,
  private val columns: Array<out Column<*, *, *, *, *, *, *>>,
  private val rows: List<InsertRows>,
  private val select: SelectNode<*, *>? = null
) : MutationHead {
  override val deletedTable
    get() = when (conflictAlgorithm) {
      CONFLICT_REPLACE -> table.name
      else -> null
    }

  override val cache get() = rows.all { it is ValuesRow }

  override val skipped get() = rows.isNotEmpty() && rows.all { it.count == 0 }

  override fun render(context: SqlRenderingContext) {
    context.sql
      .append("INSERT")
      .append(CONFLICT_VALUES[conflictAlgorithm])
      .append(" INTO ")
      .append(table.sqlSource)
    when {
      rows.isEmpty() && select == null -> context.sql.append(" DEFAULT VALUES")
      else -> {
        context.sql.append(" (")
        context.renderList(columns.size) { context.sql.append(columns[it].quotedName) }
        context.sql.append(')')
        when (select) {
          null -> renderRows(context)
          else -> {
            context.sql.append(' ')
            context.renderNested { select.render(context) }
          }
        }
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

  fun withSelect(select: SelectNode<*, *>) = InsertHead(
    table = table,
    conflictAlgorithm = conflictAlgorithm,
    columns = columns,
    rows = rows,
    select = select
  )

  fun hostRow(values: Array<out Any?>) = ValuesRow(
    Array(values.size) { index -> columns[index].encodeHost(values[index]) }
  )

  fun <T> listedRows(rows: List<T>, component: (T, Int) -> Any?) = ListedRows(
    rows = rows,
    columns = columns,
    component = component
  )
}

internal class InsertedColumns(
  private val table: Table<*>,
  private val columns: Array<out Column<*, *, *, *, *, *, *>>
) {
  private val positions = MutableObjectIntMap<Column<*, *, *, *, *, *, *>>(initialCapacity = columns.size).apply {
    columns.forEachIndexed { index, column ->
      if (!containsKey(column)) {
        this[column] = index
      }
    }
  }

  val size get() = columns.size

  operator fun get(index: Int) = columns[index]

  fun position(column: Column<*, *, *, *, *, *, *>) = positions
    .getOrDefault(
      key = column,
      defaultValue = -1
    )
    .also {
      require(it >= 0) {
        when {
          column.table !== table ->
            "INSERT column ${column.name} must be a field of the inserted ${table.name} occurrence"
          else -> "INSERT column ${column.name} is not listed in COLUMNS"
        }
      }
    }
}

internal sealed interface InsertRows : SqlRenderable {
  val count: Int
}

internal class ValuesRow(private val values: Array<Any?>) : InsertRows {
  override val count get() = 1

  override fun render(context: SqlRenderingContext) = context.renderValues(
    values = values,
    offset = 0,
    width = values.size
  )
}

internal class BuiltRows(
  private val values: Array<Any?>,
  private val width: Int,
  override val count: Int
) : InsertRows {
  override fun render(context: SqlRenderingContext) = context.renderList(count) { row ->
    context.renderValues(
      values = values,
      offset = row * width,
      width = width
    )
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

internal fun MutationNode<InsertHead>.withColumnArray(columns: Array<out Column<*, *, *, *, *, *, *>>) =
  copy(head = head.withColumns(columns))

internal fun MutationNode<InsertHead>.withSelect(query: SelectQuery<*, *>) =
  copy(head = head.withSelect(query.nestedNode()))

internal fun MutationNode<InsertHead>.withHostRow(vararg values: Any?) =
  copy(head = head.withRows(head.hostRow(values)))

internal fun MutationNode<InsertHead>.withExpressionRow(vararg values: SqlExpression<*, *, *>) =
  copy(head = head.withRows(ValuesRow(Array(values.size) { values[it].node })))

internal fun <T> MutationNode<InsertHead>.withListedRows(rows: List<T>, component: (T, Int) -> Any?) = copy(
  head = head.withRows(
    head.listedRows(
      rows = rows,
      component = component
    )
  )
)

internal fun MutationNode<InsertHead>.withBuiltRow(values: Array<Any?>) =
  copy(head = head.withRows(ValuesRow(values)))

internal fun MutationNode<InsertHead>.withBuiltRows(values: Array<Any?>, width: Int, count: Int) = copy(
  head = head.withRows(
    BuiltRows(
      values = values,
      width = width,
      count = count
    )
  )
)

private fun SqlRenderingContext.renderValues(values: Array<Any?>, offset: Int, width: Int) {
  sql.append('(')
  renderList(width) { index ->
    when (val value = values[offset + index]) {
      is NativeValue -> bind(value)
      else -> (value as ExpressionNode).render(this)
    }
  }
  sql.append(')')
}

@Suppress("UNCHECKED_CAST")
private fun Column<*, *, *, *, *, *, *>.encodeHost(value: Any?): NativeValue {
  requireHostValue(value)
  return (encode as (Any?) -> NativeValue)(value)
}
