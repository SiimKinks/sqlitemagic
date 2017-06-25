@file:Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")

package com.siimkinks.sqlitemagic

import android.support.annotation.CheckResult
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode

/** @see Column.as */
@CheckResult
inline infix fun <T, R, ET, P, C : Column<T, R, ET, P>> C.AS(alias: String): C = this.`as`(alias) as C

/** @see Column.as */
@JvmName("AS_FOR_ANONYMOUS_TABLE")
@CheckResult
inline infix fun <T, R, ET, C : Column<T, R, ET, *>> C.AS(alias: String): C = this.`as`(alias) as C

/** @see Column.concat */
@CheckResult
inline infix fun <T, R, ET, P, X : Column<*, *, *, *>> Column<T, R, ET, P>.concat(column: X) =
    this.concat(column)

typealias With = Pair<CharSequence, CharSequence>

@CheckResult
inline infix fun CharSequence.with(that: CharSequence) = With(this, that)

/** @see Column.replace */
@CheckResult
inline fun Column<String, String, CharSequence, *>.replace(with: With) =
    this.replace(with.first, with.second)

/** @see Column.is */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IS(value: T) = this.`is`(value)

/** @see Column.is */
@CheckResult
inline infix fun <T, R, ET, P, C : Column<*, *, out ET, *>> Column<T, R, ET, P>.IS(column: C) = this.`is`(column)

/** @see Column.is */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IS(select: SelectNode<out ET, Select1>) = this.`is`(select)

/** @see ComplexColumn.is */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.IS(value: Long) = this.`is`(value)

/** @see Column.isNot */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IS_NOT(value: T) = this.isNot(value)

/** @see Column.isNot */
@CheckResult
inline infix fun <T, R, ET, P, C : Column<*, *, out ET, *>> Column<T, R, ET, P>.IS_NOT(column: C) = this.isNot(column)

/** @see Column.isNot */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IS_NOT(select: SelectNode<out ET, Select1>) = this.isNot(select)

/** @see ComplexColumn.isNot */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.IS_NOT(value: Long) = this.isNot(value)

/** @see Column.like */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.LIKE(regex: String) = this.like(regex)

/** @see Column.notLike */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.NOT_LIKE(regex: String) = this.notLike(regex)

/** @see Column.glob */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.GLOB(regex: String) = this.glob(regex)

/** @see Column.notGlob */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.NOT_GLOB(regex: String) = this.notGlob(regex)

/** @see Column.in */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IN(values: Collection<T>) = this.`in`(values)

/** @see Column.in */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IN(values: Array<T>) = this.`in`(*values)

/** @see Column.in */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.IN(select: SelectNode<out ET, Select1>) = this.`in`(select)

/** @see ComplexColumn.in */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.IN(values: LongArray) = this.`in`(*values)

/** @see ComplexColumn.in */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.IN(values: Iterable<Long>) = this.`in`(values)

/** @see Column.notIn */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.NOT_IN(values: Collection<T>) = this.notIn(values)

/** @see Column.notIn */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.NOT_IN(values: Array<T>) = this.notIn(*values)

/** @see Column.notIn */
@CheckResult
inline infix fun <T, R, ET, P> Column<T, R, ET, P>.NOT_IN(select: SelectNode<out ET, Select1>) = this.notIn(select)

/** @see ComplexColumn.notIn */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.NOT_IN(values: LongArray) = this.notIn(*values)

/** @see ComplexColumn.notIn */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.NOT_IN(values: Iterable<Long>) = this.notIn(values)

/** @see NumericColumn.greaterThan */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.GREATER_THAN(value: T) = this.greaterThan(value)

/** @see NumericColumn.greaterThan */
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.GREATER_THAN(column: C) =
    this.greaterThan(column)

/** @see NumericColumn.greaterThan */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.GREATER_THAN(select: SelectNode<out ET, Select1>) =
    this.greaterThan(select)

/** @see ComplexColumn.greaterThan */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.GREATER_THAN(value: Long) = this.greaterThan(value)

/** @see NumericColumn.greaterOrEqual */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.GREATER_OR_EQUAL(value: T) = this.greaterOrEqual(value)

/** @see NumericColumn.greaterOrEqual */
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.GREATER_OR_EQUAL(column: C) =
    this.greaterOrEqual(column)

/** @see NumericColumn.greaterOrEqual */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.GREATER_OR_EQUAL(select: SelectNode<out ET, Select1>) =
    this.greaterOrEqual(select)

/** @see ComplexColumn.greaterOrEqual */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.GREATER_OR_EQUAL(value: Long) = this.greaterOrEqual(value)

/** @see NumericColumn.lessThan */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.LESS_THAN(value: T) = this.lessThan(value)

/** @see NumericColumn.lessThan */
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.LESS_THAN(column: C) =
    this.lessThan(column)

/** @see NumericColumn.lessThan */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.LESS_THAN(select: SelectNode<out ET, Select1>) =
    this.lessThan(select)

/** @see ComplexColumn.lessThan */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.LESS_THAN(value: Long) = this.lessThan(value)

/** @see NumericColumn.lessOrEqual */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.LESS_OR_EQUAL(value: T) = this.lessOrEqual(value)

/** @see NumericColumn.lessOrEqual */
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.LESS_OR_EQUAL(column: C) =
    this.lessOrEqual(column)

/** @see NumericColumn.lessOrEqual */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.LESS_OR_EQUAL(select: SelectNode<out ET, Select1>) =
    this.lessOrEqual(select)

/** @see ComplexColumn.lessOrEqual */
@CheckResult
inline infix fun <T, R, ET, P> ComplexColumn<T, R, ET, P>.LESS_OR_EQUAL(value: Long) = this.lessOrEqual(value)

typealias Between<A, B> = Pair<A, B>

@CheckResult
inline infix fun <A, B> A.AND(that: B) = Between(this, that)

/** @see NumericColumn.between */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.BETWEEN(between: Between<T, T>) =
    this.between(between.first).and(between.second)

/** @see NumericColumn.between */
@JvmName("betweenColumns")
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.BETWEEN(between: Between<C, C>) =
    this.between(between.first).and(between.second)

/** @see NumericColumn.between */
@JvmName("betweenValueAndColumn")
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.BETWEEN(between: Between<T, C>) =
    this.between(between.first).and(between.second)

/** @see NumericColumn.between */
@JvmName("betweenColumnAndValue")
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.BETWEEN(between: Between<C, T>) =
    this.between(between.first).and(between.second)

/** @see NumericColumn.notBetween */
@CheckResult
inline infix fun <T, R, ET, P> NumericColumn<T, R, ET, P>.NOT_BETWEEN(between: Between<T, T>) =
    this.notBetween(between.first).and(between.second)

/** @see NumericColumn.notBetween */
@JvmName("notBetweenColumns")
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.NOT_BETWEEN(between: Between<C, C>) =
    this.notBetween(between.first).and(between.second)

/** @see NumericColumn.notBetween */
@JvmName("notBetweenValueAndColumn")
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.NOT_BETWEEN(between: Between<T, C>) =
    this.notBetween(between.first).and(between.second)

/** @see NumericColumn.notBetween */
@JvmName("notBetweenColumnAndValue")
@CheckResult
inline infix fun <T, R, ET, P, C : NumericColumn<*, *, out ET, *>> NumericColumn<T, R, ET, P>.NOT_BETWEEN(between: Between<C, T>) =
    this.notBetween(between.first).and(between.second)

/** @see NumericColumn.add */
@CheckResult
inline operator fun <T, R, ET, P, X : NumericColumn<*, *, out Number, *>> NumericColumn<T, R, ET, P>.plus(column: X) =
    this.add(column)

/** @see NumericColumn.add */
@CheckResult
inline operator fun <T, R, ET, P> NumericColumn<T, R, ET, P>.plus(value: T) = this.add(value)

/** @see NumericColumn.sub */
@CheckResult
inline operator fun <T, R, ET, P, X : NumericColumn<*, *, out Number, *>> NumericColumn<T, R, ET, P>.minus(column: X) =
    this.sub(column)

/** @see NumericColumn.sub */
@CheckResult
inline operator fun <T, R, ET, P> NumericColumn<T, R, ET, P>.minus(value: T) = this.sub(value)

/** @see NumericColumn.mul */
@CheckResult
inline operator fun <T, R, ET, P, X : NumericColumn<*, *, out Number, *>> NumericColumn<T, R, ET, P>.times(column: X) =
    this.mul(column)

/** @see NumericColumn.mul */
@CheckResult
inline operator fun <T, R, ET, P> NumericColumn<T, R, ET, P>.times(value: T) = this.mul(value)

/** @see NumericColumn.mod */
@CheckResult
inline operator fun <T, R, ET, P, X : NumericColumn<*, *, out Number, *>> NumericColumn<T, R, ET, P>.rem(column: X) =
    this.mod(column)

/** @see NumericColumn.mod */
@CheckResult
inline operator fun <T, R, ET, P> NumericColumn<T, R, ET, P>.rem(value: T) = this.mod(value)
