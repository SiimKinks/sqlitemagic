package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import java.util.LinkedList

internal class ExprS(
  column: Column<*, *, *, *, *>,
  op: String,
  selectNode: SelectNode<*, Select1, *>
) : Expr(
  column = column,
  op = op
) {
  private val fragment = selectNode
    .selectBuilder
    .freezeFragment()

  override fun addArgs(args: ArrayList<String?>) {
    super.addArgs(args)
    fragment.addArgs(args)
  }

  override fun addDependencies(dependencies: QueryDependencies.Builder) {
    super.addDependencies(dependencies)
    fragment.addDependencies(dependencies)
  }

  override fun appendToSql(sb: StringBuilder) {
    super.appendToSql(sb)
    sb.append('(')
    fragment.appendSql(sb)
    sb.append(')')
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: MutableScatterMap<String, LinkedList<String>>
  ) {
    super.appendToSql(sb, systemRenamedTables)
    sb.append('(')
    fragment.appendSql(sb)
    sb.append(')')
  }
}
