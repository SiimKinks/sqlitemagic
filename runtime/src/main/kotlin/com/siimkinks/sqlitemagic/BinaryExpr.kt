package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

internal class BinaryExpr(
  op: String,
  private val lhs: Expr,
  private val rhs: Expr
) : Expr(
  column = null,
  op = op
) {
  override fun addArgs(args: ArrayList<String?>) {
    lhs.addArgs(args)
    rhs.addArgs(args)
  }

  override fun appendToSql(sb: StringBuilder) {
    sb.appendBinary {
      it.appendToSql(sb)
    }
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    sb.appendBinary {
      it.appendToSql(sb, systemRenamedTables)
    }
  }

  private inline fun StringBuilder.appendBinary(appendExpr: (Expr) -> Unit) {
    append('(')
    appendExpr(lhs)
    append(op)
    appendExpr(rhs)
    append(')')
  }

  override fun containsColumn(column: Column<*, *, *, *, *>) =
    lhs.containsColumn(column) || rhs.containsColumn(column)
}
