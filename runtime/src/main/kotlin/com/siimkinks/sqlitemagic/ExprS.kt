package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

internal class ExprS(
  column: Column<*, *, *, *, *>,
  op: String,
  selectNode: SelectNode<*, Select1, *>
) : Expr(
  column = column,
  op = op
) {
  private val selectBuilder = selectNode.selectBuilder
  private var parentObservedTables: ArrayList<String>? = null

  override fun addArgs(args: ArrayList<String?>) {
    super.addArgs(args)
    args.addAll(selectBuilder.args)
  }

  override fun addObservedTables(tables: ArrayList<String>) {
    super.addObservedTables(tables)
    // defer observed tables adding until sql building, where we might add missing joins,
    // so there will be more tables to add/observe
    parentObservedTables = tables
  }

  override fun appendToSql(sb: StringBuilder) {
    super.appendToSql(sb)
    sb.append('(')
    selectBuilder.appendCompiledQuery(sb, parentObservedTables)
    sb.append(')')
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    super.appendToSql(sb, systemRenamedTables)
    sb.append('(')
    selectBuilder.appendCompiledQuery(sb, parentObservedTables)
    sb.append(')')
  }
}
