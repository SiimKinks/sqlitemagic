@file:Suppress("NOTHING_TO_INLINE")

package com.siimkinks.sqlitemagic

import android.support.annotation.CheckResult
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.Update.TableNode
import com.siimkinks.sqlitemagic.Update.UpdateConflictAlgorithm

/** Builder for SQL DELETE statement. */
val UPDATE: Update
  @CheckResult get() = Update()

/** @see Update.withConflictAlgorithm */
@CheckResult
infix fun Update.WITH_CONFLICT_ALGORITHM(@ConflictAlgorithm conflictAlgorithm: Int) =
    UpdateConflictAlgorithm(this, conflictAlgorithm)

/** @see Update.table */
@CheckResult
infix fun <T> Update.TABLE(table: Table<T>) = TableNode(this, table)

/** @see Update.UpdateConflictAlgorithm.table */
@CheckResult
inline infix fun <T> UpdateConflictAlgorithm.TABLE(table: Table<T>) = this.table(table)

/** @see Update.TableNode.set */
@CheckResult
inline infix fun <V, R, ET, T> TableNode<T>.SET(v: Pair<Column<V, R, ET, T, NotNullable>, V>) =
    this.set(v.first, v.second)

/** @see Update.TableNode.set */
@JvmName("setNullable")
@CheckResult
inline infix fun <V, R, ET, T> TableNode<T>.SET(v: Pair<Column<V, R, ET, T, Nullable>, V?>) =
    this.setNullable(v.first, v.second)

/** @see Update.TableNode.set */
@JvmName("setComplexColumn")
@CheckResult
inline infix fun <V, R, ET, T, N> TableNode<T>.SET(v: Pair<ComplexColumn<V, R, ET, T, N>, Long>) =
    this.set(v.first, v.second)

/** @see Update.TableNode.set */
@JvmName("setColumn")
@CheckResult
inline infix fun <V, R, ET, T, N> TableNode<T>.SET(v: Pair<Column<V, R, ET, T, N>, Column<*, *, out ET, *, in N>>) =
    this.set(v.first, v.second)

/** @see Update.TableNode.set */
@JvmName("setSelect")
@CheckResult
inline infix fun <V, R, ET, T, N> TableNode<T>.SET(v: Pair<Column<V, R, ET, T, N>, SelectNode<out ET, Select1, in N>>) =
    this.set(v.first, v.second)

/** @see Update.Set.set */
@CheckResult
inline infix fun <V, R, ET, T> Update.Set<T>.SET(v: Pair<Column<V, R, ET, T, NotNullable>, V>) =
    this.set(v.first, v.second)

/** @see Update.Set.set */
@JvmName("setNullable")
@CheckResult
inline infix fun <V, R, ET, T> Update.Set<T>.SET(v: Pair<Column<V, R, ET, T, Nullable>, V?>) =
    this.setNullable(v.first, v.second)

/** @see Update.Set.set */
@JvmName("setComplexColumn")
@CheckResult
inline infix fun <V, R, ET, T, N> Update.Set<T>.SET(v: Pair<ComplexColumn<V, R, ET, T, N>, Long>) =
    this.set(v.first, v.second)

/** @see Update.Set.set */
@JvmName("setColumn")
@CheckResult
inline infix fun <V, R, ET, T, N> Update.Set<T>.SET(v: Pair<Column<V, R, ET, T, N>, Column<*, *, out ET, *, in N>>) =
    this.set(v.first, v.second)

/** @see Update.Set.set */
@JvmName("setSelect")
@CheckResult
inline infix fun <V, R, ET, T, N> Update.Set<T>.SET(v: Pair<Column<V, R, ET, T, N>, SelectNode<out ET, Select1, in N>>) =
    this.set(v.first, v.second)

/** @see Update.Set.where */
@CheckResult
inline infix fun <T> Update.Set<T>.WHERE(expr: Expr) = this.where(expr)
