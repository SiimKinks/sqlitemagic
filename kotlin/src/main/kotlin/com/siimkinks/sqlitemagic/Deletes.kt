@file:Suppress("NOTHING_TO_INLINE")

package com.siimkinks.sqlitemagic

import android.support.annotation.CheckResult

/** Builder for SQL DELETE statement. */
val DELETE: Delete
  @CheckResult get() = Delete()

/** @see Delete.from */
@CheckResult
infix fun <T> Delete.FROM(table: Table<T>) = Delete.From(this, table)

/** @see Delete.From.where */
@CheckResult
inline infix fun <T> Delete.From<T>.WHERE(expr: Expr) = where(expr)