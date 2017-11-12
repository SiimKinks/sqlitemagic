@file:Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")

package com.siimkinks.sqlitemagic

import android.support.annotation.CheckResult

/** @see Table.as */
@CheckResult
inline infix fun <T, TableType : Table<T>> TableType.AS(alias: String): TableType = this.`as`(alias) as TableType

/** @see Table.on */
@CheckResult
inline infix fun <T> Table<T>.ON(expr: Expr) = this.on(expr)

/** @see Table.using */
@CheckResult
inline infix fun <T, C : Column<*, *, *, *, *>> Table<T>.USING(columns: Array<C>) = this.using(*columns)
