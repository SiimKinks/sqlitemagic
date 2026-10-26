package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier

internal enum class JoinKind(
  val sql: String,
  val nullExtendsJoined: Boolean,
  val nullExtendsPrefix: Boolean
) {
  INNER(
    sql = " INNER JOIN ",
    nullExtendsJoined = false,
    nullExtendsPrefix = false
  ),
  LEFT(
    sql = " LEFT JOIN ",
    nullExtendsJoined = true,
    nullExtendsPrefix = false
  ),
  RIGHT(
    sql = " RIGHT JOIN ",
    nullExtendsJoined = false,
    nullExtendsPrefix = true
  ),
  FULL(
    sql = " FULL JOIN ",
    nullExtendsJoined = true,
    nullExtendsPrefix = true
  ),
  CROSS(
    sql = " CROSS JOIN ",
    nullExtendsJoined = false,
    nullExtendsPrefix = false
  ),
  NATURAL(
    sql = " NATURAL JOIN ",
    nullExtendsJoined = false,
    nullExtendsPrefix = false
  ),
  NATURAL_LEFT(
    sql = " NATURAL LEFT JOIN ",
    nullExtendsJoined = true,
    nullExtendsPrefix = false
  ),
  NATURAL_RIGHT(
    sql = " NATURAL RIGHT JOIN ",
    nullExtendsJoined = false,
    nullExtendsPrefix = true
  ),
  NATURAL_FULL(
    sql = " NATURAL FULL JOIN ",
    nullExtendsJoined = true,
    nullExtendsPrefix = true
  ),
  NATURAL_CROSS(
    sql = " NATURAL CROSS JOIN ",
    nullExtendsJoined = false,
    nullExtendsPrefix = false
  )
}

internal data class JoinNode(
  val kind: JoinKind,
  val table: Table<*>,
  val constraint: JoinConstraint? = null
) {
  fun render(context: SqlRenderingContext) {
    context.sql.append(kind.sql)
    table.renderSource(context)
    constraint?.render(context)
  }
}

internal sealed interface JoinConstraint : SqlRenderable {
  class On(val predicate: ExpressionNode) : JoinConstraint {
    override fun render(context: SqlRenderingContext) {
      context.sql.append(" ON ")
      predicate.render(context)
    }
  }

  class Using(private val columns: List<Column<*, *, *, *, *, *, *>>) : JoinConstraint {
    private val quotedNames = Array(columns.size) { quoteSqlIdentifier(columns[it].name) }

    override fun render(context: SqlRenderingContext) {
      context.sql.append(" USING (")
      context.renderList(count = columns.size) { index ->
        context.requireOccurrence(columns[index].table)
        context.sql.append(quotedNames[index])
      }
      context.sql.append(')')
    }
  }
}
