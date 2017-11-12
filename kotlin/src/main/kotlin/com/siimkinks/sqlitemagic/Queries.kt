@file:Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")

package com.siimkinks.sqlitemagic

import android.support.annotation.CheckResult
import com.siimkinks.sqlitemagic.Select.*

/** Builder for SQL SELECT statement. */
val SELECT: Select<SelectN>
  @CheckResult get() = Select("SELECT")

/** @see Select.column */
@CheckResult
infix fun <R, N> Select<*>.COLUMN(column: Column<*, R, *, *, N>) = SingleColumn(this as SelectSqlNode<Select1>, column)

/** @see Select.columns */
@CheckResult
infix fun <C : Column<*, *, *, *, *>> Select<SelectN>.COLUMNS(columns: Array<C>) = Columns(this, columns)

/** @see Select.distinct */
inline val Select<*>.DISTINCT
  @CheckResult get() = Select.distinct()

/** @see Select.distinct */
@CheckResult
inline infix fun <R, N> Select<*>.DISTINCT(column: Column<*, R, *, *, N>) = Select.distinct(column)

/** @see Select.distinct */
@CheckResult
inline infix fun <C : Column<*, *, *, *, *>> Select<*>.DISTINCT(columns: Array<C>) = Select.distinct(*columns)

/** @see Select.from */
@CheckResult
infix fun <T> Select<SelectN>.FROM(table: Table<T>) = From<T, T, SelectN, NotNullable>(Columns(this, ALL), table)

/** @see Select.raw */
@CheckResult
inline infix fun Select<*>.RAW(sql: String) = Select.raw(sql)

/** @see SingleColumn.from */
@CheckResult
inline infix fun <R, T, N> SingleColumn<R, N>.FROM(table: Table<T>) = this.from(table)

/** @see Columns.from */
@CheckResult
inline infix fun <T> Columns.FROM(table: Table<T>) = this.from(table)

/** @see From.join */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.JOIN(table: Table<*>) = this.join(table)

/** @see From.join */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.JOIN(joinClause: JoinClause) = this.join(joinClause)

/** @see From.leftJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.LEFT_JOIN(table: Table<*>) = this.leftJoin(table)

/** @see From.leftJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.LEFT_JOIN(joinClause: JoinClause) = this.leftJoin(joinClause)

/** @see From.leftOuterJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.LEFT_OUTER_JOIN(table: Table<*>) = this.leftOuterJoin(table)

/** @see From.leftOuterJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.LEFT_OUTER_JOIN(joinClause: JoinClause) = this.leftOuterJoin(joinClause)

/** @see From.innerJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.INNER_JOIN(table: Table<*>) = this.innerJoin(table)

/** @see From.innerJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.INNER_JOIN(joinClause: JoinClause) = this.innerJoin(joinClause)

/** @see From.crossJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.CROSS_JOIN(table: Table<*>) = this.crossJoin(table)

/** @see From.crossJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.CROSS_JOIN(joinClause: JoinClause) = this.crossJoin(joinClause)

/** @see From.naturalJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.NATURAL_JOIN(table: Table<*>) = this.naturalJoin(table)

/** @see From.naturalLeftJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.NATURAL_LEFT_JOIN(table: Table<*>) = this.naturalLeftJoin(table)

/** @see From.naturalLeftOuterJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.NATURAL_LEFT_OUTER_JOIN(table: Table<*>) = this.naturalLeftOuterJoin(table)

/** @see From.naturalInnerJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.NATURAL_INNER_JOIN(table: Table<*>) = this.naturalInnerJoin(table)

/** @see From.naturalCrossJoin */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.NATURAL_CROSS_JOIN(table: Table<*>) = this.naturalCrossJoin(table)

/** @see From.where */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.WHERE(expr: Expr) = this.where(expr)

/** @see From.groupBy */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.GROUP_BY(column: Column<*, *, *, *, *>) = this.groupBy(column)

/** @see From.groupBy */
@CheckResult
inline infix fun <T, R, S, N, C : Column<*, *, *, *, *>> From<T, R, S, N>.GROUP_BY(columns: Array<C>) = this.groupBy(*columns)

/** @see From.orderBy */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.ORDER_BY(orderingTerm: OrderingTerm) = this.orderBy(orderingTerm)

/** @see From.orderBy */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.ORDER_BY(orderingTerms: Array<OrderingTerm>) = this.orderBy(*orderingTerms)

/** @see From.limit */
@CheckResult
inline infix fun <T, R, S, N> From<T, R, S, N>.LIMIT(nrOfRows: Int) = this.limit(nrOfRows)

/** @see Where.groupBy */
@CheckResult
inline infix fun <T, S, N> Where<T, S, N>.GROUP_BY(column: Column<*, *, *, *, *>) = this.groupBy(column)

/** @see Where.groupBy */
@CheckResult
inline infix fun <T, S, N, C : Column<*, *, *, *, *>> Where<T, S, N>.GROUP_BY(columns: Array<C>) = this.groupBy(*columns)

/** @see Where.orderBy */
@CheckResult
inline infix fun <T, S, N> Where<T, S, N>.ORDER_BY(orderingTerm: OrderingTerm) = this.orderBy(orderingTerm)

/** @see Where.orderBy */
@CheckResult
inline infix fun <T, S, N> Where<T, S, N>.ORDER_BY(orderingTerms: Array<OrderingTerm>) = this.orderBy(*orderingTerms)

/** @see Where.limit */
@CheckResult
inline infix fun <T, S, N> Where<T, S, N>.LIMIT(nrOfRows: Int) = this.limit(nrOfRows)

/** @see GroupBy.having */
@CheckResult
inline infix fun <T, S, N> GroupBy<T, S, N>.HAVING(expr: Expr) = this.having(expr)

/** @see GroupBy.orderBy */
@CheckResult
inline infix fun <T, S, N> GroupBy<T, S, N>.ORDER_BY(orderingTerm: OrderingTerm) = this.orderBy(orderingTerm)

/** @see GroupBy.orderBy */
@CheckResult
inline infix fun <T, S, N> GroupBy<T, S, N>.ORDER_BY(orderingTerms: Array<OrderingTerm>) = this.orderBy(*orderingTerms)

/** @see GroupBy.limit */
@CheckResult
inline infix fun <T, S, N> GroupBy<T, S, N>.LIMIT(nrOfRows: Int) = this.limit(nrOfRows)

/** @see Having.orderBy */
@CheckResult
inline infix fun <T, S, N> Having<T, S, N>.ORDER_BY(orderingTerm: OrderingTerm) = this.orderBy(orderingTerm)

/** @see Having.orderBy */
@CheckResult
inline infix fun <T, S, N> Having<T, S, N>.ORDER_BY(orderingTerms: Array<OrderingTerm>) = this.orderBy(*orderingTerms)

/** @see Having.limit */
@CheckResult
inline infix fun <T, S, N> Having<T, S, N>.LIMIT(nrOfRows: Int) = this.limit(nrOfRows)

/** @see OrderBy.limit */
@CheckResult
inline infix fun <T, S, N> OrderBy<T, S, N>.LIMIT(nrOfRows: Int) = this.limit(nrOfRows)

/** @see Limit.offset */
@CheckResult
inline infix fun <T, S, N> Limit<T, S, N>.OFFSET(nrOfRows: Int) = this.offset(nrOfRows)

/** @see RawSelect.from */
@CheckResult
inline infix fun RawSelect.FROM(table: Table<*>) = this.from(table)

/** @see RawSelect.from */
@CheckResult
inline infix fun <T : Table<*>> RawSelect.FROM(tables: Array<T>) = this.from(*tables)

/** @see RawSelect.from */
@CheckResult
inline infix fun <T : Table<*>> RawSelect.FROM(tables: Collection<T>) = this.from(tables)

/** @see RawSelect.From.withArgs */
@CheckResult
inline infix fun RawSelect.From.WITH_ARGS(args: Array<String>) = this.withArgs(*args)

/** @see RawSelect.From.withArgs */
@CheckResult
inline infix fun RawSelect.WITH_ARGS(args: Array<String>) = this.withArgs(*args)

/** @see Select.val */
inline val <T : Number> T.value
  @CheckResult get() = Select.`val`(this)

/** @see Select.val */
inline val CharSequence.value
  @CheckResult get() = Select.`val`(this)

/** @see Select.val */
inline val Any.value
  @CheckResult get() = Select.`val`(this)
