@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.CompoundOperator

/**
 * Connect together SELECT statements to form a compound SELECT using the UNION operator.
 *
 * The UNION operator works the same way as UNION ALL, except that duplicate rows
 * are removed from the final result set.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
infix fun <T, S : Selection<T>> SelectQuery<T, S>.UNION(other: SelectQuery<out T, *>) = compound(
  operator = CompoundOperator.UNION,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the UNION operator.
 *
 * The UNION operator works the same way as UNION ALL, except that duplicate rows
 * are removed from the final result set.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * The result is nullable, since [other] selects nullable values.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
@JvmName("nullableUnion")
infix fun <R, T> SelectQuery<T, ScalarSelection<R, Required, T>>.UNION(
  other: SelectQuery<T?, out ScalarSelection<*, SqlNullable, T?>>
) = nullableCompound(
  operator = CompoundOperator.UNION,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the UNION ALL operator.
 *
 * A compound SELECT created using UNION ALL operator returns all the rows from the SELECT
 * to the left of the UNION ALL operator, and all the rows from the SELECT to the right of it.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
infix fun <T, S : Selection<T>> SelectQuery<T, S>.UNION_ALL(other: SelectQuery<out T, *>) = compound(
  operator = CompoundOperator.UNION_ALL,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the UNION ALL operator.
 *
 * A compound SELECT created using UNION ALL operator returns all the rows from the SELECT
 * to the left of the UNION ALL operator, and all the rows from the SELECT to the right of it.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * The result is nullable, since [other] selects nullable values.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
@JvmName("nullableUnionAll")
infix fun <R, T> SelectQuery<T, ScalarSelection<R, Required, T>>.UNION_ALL(
  other: SelectQuery<T?, out ScalarSelection<*, SqlNullable, T?>>
) = nullableCompound(
  operator = CompoundOperator.UNION_ALL,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the INTERSECT operator.
 *
 * The INTERSECT operator returns the intersection of the results of the left and right SELECTs.
 * Duplicate rows are removed from the results before the result set is returned.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
infix fun <T, S : Selection<T>> SelectQuery<T, S>.INTERSECT(other: SelectQuery<out T, *>) = compound(
  operator = CompoundOperator.INTERSECT,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the INTERSECT operator.
 *
 * The INTERSECT operator returns the intersection of the results of the left and right SELECTs.
 * Duplicate rows are removed from the results before the result set is returned.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * The result stays not nullable, since the result never contains values that only [other] selects.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
@JvmName("requiredIntersect")
infix fun <R, T> SelectQuery<T, ScalarSelection<R, Required, T>>.INTERSECT(
  other: SelectQuery<T?, out ScalarSelection<*, SqlNullable, T?>>
) = compound(
  operator = CompoundOperator.INTERSECT,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the EXCEPT operator.
 *
 * The EXCEPT operator returns the subset of rows returned by the left SELECT
 * that are not also returned by the right-hand SELECT. Duplicate rows are
 * removed from the results before the result set is returned.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
infix fun <T, S : Selection<T>> SelectQuery<T, S>.EXCEPT(other: SelectQuery<out T, *>) = compound(
  operator = CompoundOperator.EXCEPT,
  other = other
)

/**
 * Connect together SELECT statements to form a compound SELECT using the EXCEPT operator.
 *
 * The EXCEPT operator returns the subset of rows returned by the left SELECT
 * that are not also returned by the right-hand SELECT. Duplicate rows are
 * removed from the results before the result set is returned.
 *
 * In a compound SELECT, all the constituent SELECTs must return the same number of
 * result columns. All rows are read the same way as this query's rows, without the `mapTo` of [other].
 * ORDER BY and LIMIT clauses added to the compound SELECT apply to the entire compound SELECT. If [other] is itself
 * a compound SELECT or has its own WITH, ORDER BY or LIMIT clause, it is rendered as `SELECT * FROM (other)`.
 *
 * The result stays not nullable, since the result never contains values that only [other] selects.
 *
 * @param other Connected SELECT statement to form a compound SELECT
 * @return SQL SELECT statement builder
 * @see [SQLite documentation: Compound Select Statements](https://www.sqlite.org/lang_select.html#compound)
 */
@JvmName("requiredExcept")
infix fun <R, T> SelectQuery<T, ScalarSelection<R, Required, T>>.EXCEPT(
  other: SelectQuery<T?, out ScalarSelection<*, SqlNullable, T?>>
) = compound(
  operator = CompoundOperator.EXCEPT,
  other = other
)

private fun <T, S : Selection<T>> SelectQuery<T, S>.compound(
  operator: CompoundOperator,
  other: SelectQuery<*, *>
) = SelectCompoundStage(
  node.compound(
    operator = operator,
    arm = other.node
  )
)

private fun <R, T> SelectQuery<T, ScalarSelection<R, Required, T>>.nullableCompound(
  operator: CompoundOperator,
  other: SelectQuery<*, *>
) = SelectCompoundStage(
  node
    .withSelection(node.selection.nullable())
    .compound(
      operator = operator,
      arm = other.node
    )
)
