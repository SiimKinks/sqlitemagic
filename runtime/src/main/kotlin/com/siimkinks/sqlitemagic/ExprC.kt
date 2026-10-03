package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import java.util.LinkedList

internal class ExprC(
  column: Column<*, *, *, *, *>,
  op: String,
  private val exprColumn: Column<*, *, *, *, *>
) : Expr(
  column = column,
  op = op
) {
  override fun addArgs(args: ArrayList<String?>) {
    super.addArgs(args)
    exprColumn.addArgs(args)
  }

  override fun addDependencies(dependencies: QueryDependencies.Builder) {
    super.addDependencies(dependencies)
    exprColumn.addDependencies(dependencies)
  }

  override fun appendToSql(sb: StringBuilder) {
    super.appendToSql(sb)
    sb.appendExprColumn {
      exprColumn.appendSql(sb)
    }
  }

  override fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: MutableScatterMap<String, LinkedList<String>>
  ) {
    super.appendToSql(sb, systemRenamedTables)
    sb.appendExprColumn {
      exprColumn.appendSql(sb, systemRenamedTables)
    }
  }

  private inline fun StringBuilder.appendExprColumn(
    appendColumn: () -> Unit
  ) {
    when {
      exprColumn.hasAlias() -> append(exprColumn.getAppendableAlias())
      else -> appendColumn()
    }
  }

  override fun containsColumn(column: Column<*, *, *, *, *>) =
    super.containsColumn(column) || column == exprColumn
}
