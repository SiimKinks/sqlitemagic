package com.siimkinks.sqlitemagic

internal class ExprN(
  column: Column<*, *, *, *, *>,
  op: String,
  private val evalArgs: Array<String>
) : Expr(
  column = column,
  op = op
) {
  override fun addArgs(args: ArrayList<String?>) {
    evalArgs.forEach(args::add)
  }
}
