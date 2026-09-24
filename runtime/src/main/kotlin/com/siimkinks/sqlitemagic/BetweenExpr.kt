package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

internal class BetweenExpr(
  column: Column<*, *, *, *, *>,
  private val firstVal: String?,
  private val secondVal: String?,
  private val firstColumn: Column<*, *, *, *, *>?,
  private val secondColumn: Column<*, *, *, *, *>?,
  not: Boolean
) : Expr(column = column, op = if (not) " NOT " else " ") {
  override fun appendToSql(sb: StringBuilder) {
    super.appendToSql(sb)
    sb.appendBetween {
      it.appendSql(sb)
    }
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    super.appendToSql(sb, systemRenamedTables)
    sb.appendBetween {
      it.appendSql(sb, systemRenamedTables)
    }
  }

  private inline fun StringBuilder.appendBetween(appendColumn: (Column<*, *, *, *, *>) -> Unit) {
    append("BETWEEN ")
    when {
      firstVal != null -> append('?')
      else -> appendColumn(checkNotNull(firstColumn) {
        "Missing BETWEEN statement first value"
      })
    }
    append(" AND ")
    when {
      secondVal != null -> append('?')
      else -> appendColumn(checkNotNull(secondColumn) {
        "Missing BETWEEN statement second value"
      })
    }
  }

  override fun addArgs(args: ArrayList<String?>) {
    super.addArgs(args)
    firstVal?.let(args::add)
    secondVal?.let(args::add)
  }

  override fun containsColumn(column: Column<*, *, *, *, *>) =
    super.containsColumn(column) ||
        column == firstColumn ||
        column == secondColumn
}
