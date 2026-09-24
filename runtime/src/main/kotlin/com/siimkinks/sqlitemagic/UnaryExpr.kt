package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

internal class UnaryExpr(
  op: String,
  private val expr: Expr
) : Expr(
  column = null,
  op = op
) {
  override fun addArgs(args: ArrayList<String?>) {
    expr.addArgs(args)
  }

  override fun appendToSql(sb: StringBuilder) {
    sb.appendUnary {
      expr.appendToSql(sb)
    }
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    sb.appendUnary {
      expr.appendToSql(sb, systemRenamedTables)
    }
  }

  private inline fun StringBuilder.appendUnary(
    appendExpr: () -> Unit
  ) {
    append(op)
    append('(')
    appendExpr()
    append(')')
  }

  override fun containsColumn(column: Column<*, *, *, *, *>) = expr.containsColumn(column)
}
