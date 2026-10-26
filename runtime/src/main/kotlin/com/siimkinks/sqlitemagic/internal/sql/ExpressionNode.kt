package com.siimkinks.sqlitemagic.internal.sql

import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.SqlOperator
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.WindowSpec
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier

internal sealed interface ExpressionNode : SqlRenderable {
  fun renderOperand(context: SqlRenderingContext) = render(context)

  fun outerJoinNull(context: SqlRenderingContext): Field? = null

  fun forEachField(action: (Field) -> Unit) = Unit

  class Field(val table: Table<*>, val name: String) : ExpressionNode {
    val quotedName = quoteSqlIdentifier(name)

    override fun outerJoinNull(context: SqlRenderingContext) = takeIf { context.isNullExtended(table) }

    override fun forEachField(action: (Field) -> Unit) = action(this)

    override fun render(context: SqlRenderingContext) {
      val qualifier = context.qualifier(table)
      if (qualifier.isNotEmpty()) {
        context.sql
          .append(qualifier)
          .append('.')
      }
      context.sql.append(quotedName)
    }

    override fun toString() = when {
      table.sqlQualifier.isEmpty() -> quotedName
      else -> "${table.sqlQualifier}.$quotedName"
    }
  }

  data class Bound(val argument: NativeValue) : ExpressionNode {
    override fun render(context: SqlRenderingContext) = context.bind(argument)
  }

  data class Literal(val argument: NativeValue) : ExpressionNode {
    override fun render(context: SqlRenderingContext) = context.appendLiteral(argument)
  }

  data class Parameter(val parameter: ParameterIdentity<*>) : ExpressionNode {
    override fun render(context: SqlRenderingContext) = context.bind(parameter)
  }

  data class Raw(
    val sql: String,
    val arguments: List<NativeValue>,
    val dependencies: QueryDependencies
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append(sql)
      context.bindings.addAll(arguments)
      context.dependencies.merge(dependencies)
    }

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)

    fun observing(table: Table<*>, tables: Array<out Table<*>>) = copy(
      dependencies = QueryDependencies.declared(
        table = table,
        tables = tables
      )
    )
  }

  class Binary(
    val left: ExpressionNode,
    val operator: SqlOperator,
    val right: ExpressionNode
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      left.renderOperand(context)
      context.sql
        .append(' ')
        .append(operator.sql)
        .append(' ')
      right.renderOperand(context)
    }

    override fun outerJoinNull(context: SqlRenderingContext) = left.outerJoinNull(context)
      ?: right.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) {
      left.forEachField(action)
      right.forEachField(action)
    }

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  class Not(val operand: ExpressionNode) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append("NOT ")
      operand.renderOperand(context)
    }

    override fun outerJoinNull(context: SqlRenderingContext) = operand.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) = operand.forEachField(action)

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  class HostMembership(
    val operand: ExpressionNode,
    val arguments: List<NativeValue>,
    val negated: Boolean
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      operand.renderOperand(context)
      context.sql.append(
        when {
          negated -> " NOT IN ("
          else -> " IN ("
        }
      )
      context.renderList(
        items = arguments,
        render = context::bind
      )
      context.sql.append(')')
    }

    override fun outerJoinNull(context: SqlRenderingContext) = operand.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) = operand.forEachField(action)

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  class Range(
    val operand: ExpressionNode,
    val lower: ExpressionNode,
    val upper: ExpressionNode,
    val negated: Boolean
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      operand.renderOperand(context)
      context.sql.append(
        when {
          negated -> " NOT BETWEEN "
          else -> " BETWEEN "
        }
      )
      lower.renderOperand(context)
      context.sql.append(" AND ")
      upper.renderOperand(context)
    }

    override fun outerJoinNull(context: SqlRenderingContext) = operand.outerJoinNull(context)
      ?: lower.outerJoinNull(context)
      ?: upper.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) {
      operand.forEachField(action)
      lower.forEachField(action)
      upper.forEachField(action)
    }

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  class Escape(
    val pattern: Binary,
    val escape: ExpressionNode
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      pattern.render(context)
      context.sql.append(" ESCAPE ")
      escape.renderOperand(context)
    }

    override fun outerJoinNull(context: SqlRenderingContext) = pattern.outerJoinNull(context)
      ?: escape.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) {
      pattern.forEachField(action)
      escape.forEachField(action)
    }

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  class Subquery(val select: SelectNode<*, *>) : ExpressionNode {
    override fun render(context: SqlRenderingContext) = select.renderNested(context)
  }

  class Exists(
    val select: SelectNode<*, *>,
    val negated: Boolean
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append(
        when {
          negated -> "NOT EXISTS "
          else -> "EXISTS "
        }
      )
      select.renderNested(context)
    }

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  /** Always grouped, so a negative operand never renders as an SQL `--` comment. */
  class Negate(val operand: ExpressionNode) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append("-(")
      operand.render(context)
      context.sql.append(')')
    }

    override fun outerJoinNull(context: SqlRenderingContext) = operand.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) = operand.forEachField(action)

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }

  /** [requiredBy] is the argument whose NULL a required result follows, or -1. */
  class Call(
    val function: String,
    val arguments: List<ExpressionNode>,
    val requiredBy: Int,
    val dependencies: QueryDependencies = QueryDependencies.NONE
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql
        .append(function)
        .append('(')
      context.renderList(arguments) { it.render(context) }
      context.sql.append(')')
      context.dependencies.merge(dependencies)
    }

    override fun outerJoinNull(context: SqlRenderingContext) = when (requiredBy) {
      -1 -> null
      else -> arguments[requiredBy].outerJoinNull(context)
    }

    override fun forEachField(action: (Field) -> Unit) = arguments.forEach { it.forEachField(action) }
  }

  /** [branches] alternate each WHEN condition or match with its THEN value. */
  class Case(
    val base: ExpressionNode?,
    val branches: List<ExpressionNode>,
    val otherwise: ExpressionNode?
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append("CASE")
      if (base != null) {
        context.sql.append(' ')
        base.render(context)
      }
      branches.forEachIndexed { index, branch ->
        context.sql.append(
          when {
            index % 2 == 0 -> " WHEN "
            else -> " THEN "
          }
        )
        branch.render(context)
      }
      if (otherwise != null) {
        context.sql.append(" ELSE ")
        otherwise.render(context)
      }
      context.sql.append(" END")
    }

    override fun outerJoinNull(context: SqlRenderingContext): Field? {
      for (index in 1 until branches.size step 2) {
        branches[index]
          .outerJoinNull(context)
          ?.let { return it }
      }
      return otherwise?.outerJoinNull(context)
    }

    override fun forEachField(action: (Field) -> Unit) {
      base?.forEachField(action)
      branches.forEach { it.forEachField(action) }
      otherwise?.forEachField(action)
    }
  }

  /** An outer join never makes an aggregate result NULL. */
  class Aggregate(
    val function: String,
    val arguments: List<ExpressionNode>,
    val distinct: Boolean,
    val filter: ExpressionNode? = null
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql
        .append(function)
        .append('(')
      if (distinct) {
        context.sql.append("DISTINCT ")
      }
      when {
        arguments.isEmpty() -> context.sql.append('*')
        else -> context.renderList(arguments) { it.render(context) }
      }
      context.sql.append(')')
      if (filter != null) {
        context.sql.append(" FILTER (WHERE ")
        filter.render(context)
        context.sql.append(')')
      }
    }

    override fun forEachField(action: (Field) -> Unit) {
      arguments.forEach { it.forEachField(action) }
      filter?.forEachField(action)
    }

    fun filtered(predicate: ExpressionNode) = Aggregate(
      function = function,
      arguments = arguments,
      distinct = distinct,
      filter = predicate
    )
  }

  class Over(
    val function: ExpressionNode,
    val window: WindowSpec
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      function.render(context)
      context.sql.append(" OVER ")
      window.render(context)
    }

    override fun forEachField(action: (Field) -> Unit) {
      function.forEachField(action)
      window.forEachField(action)
    }
  }

  class Collate(
    val operand: ExpressionNode,
    val collation: String
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      operand.renderOperand(context)
      context.sql
        .append(" COLLATE ")
        .append(collation)
    }

    override fun outerJoinNull(context: SqlRenderingContext) = operand.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) = operand.forEachField(action)
  }

  class Cast(
    val operand: ExpressionNode,
    val type: String
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append("CAST(")
      operand.render(context)
      context.sql
        .append(" AS ")
        .append(type)
        .append(')')
    }

    override fun outerJoinNull(context: SqlRenderingContext) = operand.outerJoinNull(context)

    override fun forEachField(action: (Field) -> Unit) = operand.forEachField(action)
  }

  /** A row value, or the list of an IN operator; an empty list renders as `IN ()`, which SQLite supports. */
  class Row(val elements: List<ExpressionNode>) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      context.sql.append('(')
      context.renderList(elements) { it.render(context) }
      context.sql.append(')')
    }

    override fun outerJoinNull(context: SqlRenderingContext) = elements.firstNotNullOfOrNull {
      it.outerJoinNull(context)
    }

    override fun forEachField(action: (Field) -> Unit) = elements.forEach { it.forEachField(action) }
  }

  class NullTest(
    val operand: ExpressionNode,
    val negated: Boolean
  ) : ExpressionNode {
    override fun render(context: SqlRenderingContext) {
      operand.renderOperand(context)
      context.sql.append(
        when {
          negated -> " IS NOT NULL"
          else -> " IS NULL"
        }
      )
    }

    override fun forEachField(action: (Field) -> Unit) = operand.forEachField(action)

    override fun renderOperand(context: SqlRenderingContext) = renderGrouped(context)
  }
}

private fun SelectNode<*, *>.renderNested(context: SqlRenderingContext) {
  context.sql.append('(')
  context.renderNested { render(context) }
  context.sql.append(')')
}

private fun ExpressionNode.renderGrouped(context: SqlRenderingContext) {
  context.sql.append('(')
  render(context)
  context.sql.append(')')
}
