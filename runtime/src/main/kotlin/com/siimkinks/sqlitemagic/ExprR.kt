package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

internal class ExprR(
  op: String,
  private val evalArgs: Array<out String>
) : Expr(
  column = null,
  op = op
) {
  override fun addArgs(args: ArrayList<String?>) {
    evalArgs.forEach(args::add)
  }

  override fun appendToSql(sb: StringBuilder) {
    sb.append(op)
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    sb.append(op)
  }

  override fun containsColumn(column: Column<*, *, *, *, *>) = column.nameInQuery in op
}
