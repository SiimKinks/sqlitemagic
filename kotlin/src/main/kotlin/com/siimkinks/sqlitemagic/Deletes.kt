@file:Suppress("NOTHING_TO_INLINE")

package com.siimkinks.sqlitemagic

import android.support.annotation.CheckResult

/** Builder for SQL DELETE statement. */
val DELETE: Delete
  @CheckResult get() = Delete()

/** @see Delete.from */
@CheckResult
infix fun <T> Delete.FROM(table: Table<T>) = Delete.From(this, table.name)

/** @see Delete.from */
@CheckResult
infix fun Delete.FROM(tableName: String) = Delete.From(this, tableName)

/** @see Delete.From.where */
@CheckResult
inline infix fun Delete.From.WHERE(expr: Expr) = where(expr)

/** @see Delete.From.where */
@CheckResult
inline infix fun Delete.From.WHERE(whereClause: String) =
    where(whereClause)

/** @see Delete.From.where */
@CheckResult
inline infix fun Delete.From.WHERE(whereClause: Pair<String, Array<String>>) =
    where(whereClause.first, *whereClause.second)
