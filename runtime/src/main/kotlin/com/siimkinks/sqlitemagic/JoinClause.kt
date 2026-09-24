package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

/** An SQL join clause. */
open class JoinClause internal constructor(
  internal val table: Table<*>,
  internal var operator: String,
  private val constraint: String?
) : SqlClause() {
  val tableNameInQuery get() = table.nameInQuery

  override fun appendSql(sb: StringBuilder) = appendJoin(sb)

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) = appendJoin(sb)

  private fun appendJoin(sb: StringBuilder) {
    sb.append(operator)
      .append(' ')
    table.appendToSqlFromClause(sb)
    if (!constraint.isNullOrEmpty()) {
      sb.append(' ')
        .append(constraint)
    }
  }

  internal open fun containsColumn(column: Column<*, *, *, *, *>): Boolean = true

  internal open fun addArgs(args: ArrayList<String?>) = Unit

  companion object {
    internal fun indexOf(
      table: Table<*>,
      array: List<JoinClause>,
      joinedOnColumn: Column<*, *, *, *, *>
    ) = array.indexOfFirst {
      it.table.baseNameEquals(table) && it.containsColumn(joinedOnColumn)
    }
  }
}
