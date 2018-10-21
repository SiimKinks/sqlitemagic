@file:Suppress("NOTHING_TO_INLINE")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult

/** @see Expr.raw */
inline val String.expr
  @CheckResult get() = Expr.raw(this)

/** @see Expr.raw */
@CheckResult
inline fun String.expr(vararg args: String) = Expr.raw(this, *args)

/** @see Expr.and */
@CheckResult
inline infix fun Expr.AND(expr: Expr) = this.and(expr)

/** @see Expr.andNot */
@CheckResult
inline infix fun Expr.AND_NOT(expr: Expr) = this.andNot(expr)

/** @see Expr.or */
@CheckResult
inline infix fun Expr.OR(expr: Expr) = this.or(expr)

/** @see Expr.orNot */
@CheckResult
inline infix fun Expr.OR_NOT(expr: Expr) = this.orNot(expr)
