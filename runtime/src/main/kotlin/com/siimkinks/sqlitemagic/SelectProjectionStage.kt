@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.JoinConstraint
import com.siimkinks.sqlitemagic.internal.sql.SelectNode

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectProjectionStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  WhereClause<T, S>,
  GroupClause<T, S>,
  OrderClause<T, S>,
  LimitClause<T, S> {
  /**
   * Define a FROM clause.
   *
   * @param source Table, subquery or common table expression to select from. Tables must be one of
   *               annotation processor generated table objects that corresponds to table in a database
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun FROM(source: Source) = SelectFromStage(node.copy(source = source.occurrence()))
}

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectFromStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  JoinClause<T, S>,
  WhereClause<T, S>,
  GroupClause<T, S>,
  OrderClause<T, S>,
  LimitClause<T, S>

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectJoinStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  JoinClause<T, S>,
  WhereClause<T, S>,
  GroupClause<T, S>,
  OrderClause<T, S>,
  LimitClause<T, S> {
  /**
   * Define a join ON constraint.
   *
   * @param predicate Expression to use in join `ON` clause
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun ON(predicate: SqlExpression<Boolean, *, *>) = SelectFromStage(
    node.withJoinConstraint(JoinConstraint.On(predicate.node))
  )

  /**
   * Define a join USING constraint.
   *
   * Each of the columns specified must exist in the datasets to both the left and right
   * of the join-operator. For each pair of columns, the expression `lhs.X = rhs.X`
   * is evaluated for each row of the cartesian product as a boolean expression.
   * Only rows for which all such expressions evaluates to true are included from the
   * result set. When comparing values as a result of a USING clause, the normal rules
   * for handling affinities, collation sequences and NULL values in comparisons apply.
   * The column from the dataset on the left-hand side of the join-operator is considered
   * to be on the left-hand side of the comparison operator (=) for the purposes of
   * collation sequence and affinity precedence.
   *
   * The column must belong to the joined source or to a source before it; only its name is used in the SQL.
   *
   * @param column Column to use in the USING clause
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun USING(column: Column<*, *, *, *, *, *, *>) = SelectFromStage(
    node.withJoinConstraint(JoinConstraint.Using(listOf(column)))
  )

  /**
   * Define a join USING constraint.
   *
   * Each of the columns specified must exist in the datasets to both the left and right
   * of the join-operator. For each pair of columns, the expression `lhs.X = rhs.X`
   * is evaluated for each row of the cartesian product as a boolean expression.
   * Only rows for which all such expressions evaluates to true are included from the
   * result set. When comparing values as a result of a USING clause, the normal rules
   * for handling affinities, collation sequences and NULL values in comparisons apply.
   * The column from the dataset on the left-hand side of the join-operator is considered
   * to be on the left-hand side of the comparison operator (=) for the purposes of
   * collation sequence and affinity precedence.
   *
   * Each column must belong to the joined source or to a source before it; only its name is used in the SQL.
   *
   * Example:
   * ```
   * val peer = AUTHOR AS "peer"
   * SELECT FROM AUTHOR INNER_JOIN peer USING arrayOf(peer.ID, peer.NAME)
   * ```
   *
   * @param columns Columns to use in the USING clause
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun USING(columns: Array<out Column<*, *, *, *, *, *, *>>) = SelectFromStage(
    node.withJoinConstraint(JoinConstraint.Using(columns.toList()))
  )

  /**
   * Define a join USING constraint.
   *
   * Each of the columns specified must exist in the datasets to both the left and right
   * of the join-operator. For each pair of columns, the expression `lhs.X = rhs.X`
   * is evaluated for each row of the cartesian product as a boolean expression.
   * Only rows for which all such expressions evaluates to true are included from the
   * result set. When comparing values as a result of a USING clause, the normal rules
   * for handling affinities, collation sequences and NULL values in comparisons apply.
   * The column from the dataset on the left-hand side of the join-operator is considered
   * to be on the left-hand side of the comparison operator (=) for the purposes of
   * collation sequence and affinity precedence.
   *
   * Each column must belong to the joined source or to a source before it; only its name is used in the SQL.
   *
   * @param first The first column to use in the USING clause
   * @param second The second column to use in the USING clause
   * @param additional Additional columns to use in the USING clause
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun USING(
    first: Column<*, *, *, *, *, *, *>,
    second: Column<*, *, *, *, *, *, *>,
    vararg additional: Column<*, *, *, *, *, *, *>
  ) = SelectFromStage(
    node.withJoinConstraint(JoinConstraint.Using(listOf(first, second, *additional)))
  )
}

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectWhereStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  GroupClause<T, S>,
  OrderClause<T, S>,
  LimitClause<T, S>

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectGroupStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  OrderClause<T, S>,
  LimitClause<T, S> {
  /**
   * Define a HAVING clause.
   *
   * @param predicate HAVING clause expression
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun HAVING(predicate: SqlExpression<Boolean, *, *>) = SelectHavingStage(
    node.copy(having = predicate.node)
  )
}

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectHavingStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  OrderClause<T, S>,
  LimitClause<T, S>

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectOrderStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  LimitClause<T, S>

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectLimitStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode) {
  /**
   * Add an OFFSET clause to the query.
   *
   * @param value Number of rows to skip from the start of the result
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun OFFSET(value: Long) = SelectOffsetStage(node.copy(offset = value))

  /**
   * Add an OFFSET clause to the query.
   *
   * @param value Number of rows to skip from the start of the result
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun OFFSET(value: Int) = OFFSET(value.toLong())
}

/**
 * Builder for compound SQL SELECT statement.
 *
 * ORDER BY and LIMIT clauses apply to the entire compound SELECT.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectCompoundStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode),
  OrderClause<T, S>,
  LimitClause<T, S>

/**
 * Builder for SQL SELECT statement.
 *
 * @param T Selection return type
 * @param S Selection type
 */
class SelectOffsetStage<T, S : Selection<T>> internal constructor(
  initialNode: SelectNode<T, S>
) : SelectQuery<T, S>(initialNode)
