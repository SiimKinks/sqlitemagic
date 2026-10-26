package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.CteDeclaration
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.EntitySelection
import com.siimkinks.sqlitemagic.Ordering
import com.siimkinks.sqlitemagic.Selection
import com.siimkinks.sqlitemagic.Source
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.occurrence

internal data class SelectNode<out T, out S : Selection<T>>(
  val selection: S,
  val distinct: Boolean = false,
  val where: ExpressionNode? = null,
  val source: Table<*>? = null,
  val joins: List<JoinNode> = emptyList(),
  val groupings: List<ExpressionNode> = emptyList(),
  val having: ExpressionNode? = null,
  val orderings: List<Ordering> = emptyList(),
  val limit: Long? = null,
  val offset: Long? = null,
  val connection: DbConnectionImpl? = null,
  val compound: Compound? = null,
  val with: List<CteDeclaration> = emptyList()
) : SqlRenderable {
  override fun render(context: SqlRenderingContext) {
    if (with.isNotEmpty()) {
      context.renderWith(with)
    }
    when (compound) {
      null -> renderSelect(context)
      else -> compound.render(context)
    }
    if (orderings.isNotEmpty()) {
      context.sql.append(" ORDER BY ")
      context.renderList(items = orderings) { it.render(context) }
    }
    if (limit != null) {
      context.sql
        .append(" LIMIT ")
        .append(limit)
    }
    if (offset != null) {
      context.sql
        .append(" OFFSET ")
        .append(offset)
    }
  }

  private fun renderSelect(context: SqlRenderingContext) {
    val sources = prepareSources(
      node = this,
      context = context
    )
    context.sql.append("SELECT ")
    if (distinct) {
      context.sql.append("DISTINCT ")
    }
    selection.render(context)
    sources?.render(context)
    if (where != null) {
      context.sql.append(" WHERE ")
      where.render(context)
    }
    if (groupings.isNotEmpty()) {
      context.sql.append(" GROUP BY ")
      context.renderList(items = groupings) { it.render(context) }
    }
    if (having != null) {
      context.sql.append(" HAVING ")
      having.render(context)
    }
  }

  fun <R, Q : Selection<R>> withSelection(selection: Q) = SelectNode(
    selection = selection,
    distinct = distinct,
    where = where,
    source = source,
    joins = joins,
    groupings = groupings,
    having = having,
    orderings = orderings,
    limit = limit,
    offset = offset,
    connection = connection,
    compound = compound?.withFirstSelection(selection),
    with = with
  )

  fun compound(operator: CompoundOperator, arm: SelectNode<*, *>): SelectNode<T, S> {
    require(connection == null && arm.connection == null) {
      "A compound's arms cannot use a connection; choose it on the compound"
    }
    val next = CompoundArm(
      operator = operator,
      select = arm
    )
    return when {
      compound != null && orderings.isEmpty() && limit == null && offset == null -> copy(
        compound = Compound(
          first = compound.first,
          arms = compound.arms + next
        )
      )
      else -> SelectNode(
        selection = selection,
        compound = Compound(
          first = this,
          arms = listOf(next)
        )
      )
    }
  }

  fun withDeepArms() = when (compound) {
    null -> this
    else -> copy(
      compound = Compound(
        first = compound.first,
        arms = compound.arms.map { arm ->
          CompoundArm(
            operator = arm.operator,
            select = arm.select.deepened()
          )
        }
      )
    )
  }

  fun withJoin(kind: JoinKind, source: Source) = copy(
    joins = joins + JoinNode(
      kind = kind,
      table = source.occurrence()
    )
  )

  fun withJoinConstraint(constraint: JoinConstraint) = copy(
    joins = joins.dropLast(1) + joins.last().copy(constraint = constraint)
  )
}

internal enum class CompoundOperator(val sql: String) {
  UNION(" UNION "),
  UNION_ALL(" UNION ALL "),
  INTERSECT(" INTERSECT "),
  EXCEPT(" EXCEPT ")
}

internal class Compound(
  val first: SelectNode<*, *>,
  val arms: List<CompoundArm>
) {
  fun render(context: SqlRenderingContext) {
    first.render(context)
    arms.forEach { it.render(context) }
  }

  fun withFirstSelection(selection: Selection<*>): Compound = Compound(
    first = first.withSelection(selection),
    arms = arms
  )
}

internal class CompoundArm(
  val operator: CompoundOperator,
  val select: SelectNode<*, *>
) {
  fun render(context: SqlRenderingContext) {
    context.sql.append(operator.sql)
    context.renderArm {
      when {
        select.rendersAsArm -> select.render(context)
        else -> {
          context.sql.append("SELECT * FROM (")
          select.render(context)
          context.sql.append(')')
        }
      }
    }
  }
}

private val SelectNode<*, *>.rendersAsArm
  get() = compound == null && with.isEmpty() && orderings.isEmpty() && limit == null && offset == null

private fun SelectNode<*, *>.deepened(): SelectNode<*, *> = when (val selection = selection) {
  is EntitySelection<*> -> withSelection(selection.deep())
  else -> this
}.withDeepArms()
