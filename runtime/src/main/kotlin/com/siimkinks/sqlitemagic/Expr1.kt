package com.siimkinks.sqlitemagic

internal class Expr1(
  column: Column<*, *, *, *, *>,
  op: String,
  private val evalArg: String?
) : Expr(
  column = column,
  op = op
) {
  override fun addArgs(args: ArrayList<String?>) {
    super.addArgs(args)
    args.add(evalArg)
  }
}
