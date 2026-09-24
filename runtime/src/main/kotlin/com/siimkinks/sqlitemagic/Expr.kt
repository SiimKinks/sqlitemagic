package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Select.OrderingTerm
import com.siimkinks.sqlitemagic.Select.OrderingTerm.Companion.ASC
import com.siimkinks.sqlitemagic.Select.OrderingTerm.Companion.DESC
import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_STRINGS
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

/** An SQL expression. */
open class Expr internal constructor(
  private val column: Column<*, *, *, *, *>?,
  protected val op: String
) {
  internal open fun addArgs(args: ArrayList<String?>) = Unit

  internal open fun addObservedTables(tables: ArrayList<String>) = Unit

  internal open fun appendToSql(sb: StringBuilder) {
    checkNotNull(column).appendSql(sb)
    sb.append(op)
  }

  internal open fun appendToSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    checkNotNull(column).appendSql(sb, systemRenamedTables)
    sb.append(op)
  }

  internal open fun containsColumn(column: Column<*, *, *, *, *>) = column == this.column

  companion object {
    /**
     * Create SQLite expression from raw SQL string.
     *
     * @param rawExpr Raw expression
     * @return Raw SQL expression.
     */
    @CheckResult
    fun raw(rawExpr: String): Expr = ExprR(rawExpr, EMPTY_STRINGS)

    /**
     * Create SQLite expression from raw SQL string.
     *
     * @param rawExpr Raw expression
     * @param args Expression arguments
     * @return Raw SQL expression.
     */
    @CheckResult
    fun raw(rawExpr: String, vararg args: String): Expr = ExprR(rawExpr, args)
  }

  /**
   * Create ORDER BY ordering term.
   *
   * Orders rows in ascending order.
   *
   * @return Ordering term to be used in `orderBy` method.
   */
  @CheckResult
  fun asc(): OrderingTerm = OrderingTerm(null, this, ASC)

  /**
   * Create ORDER BY ordering term.
   *
   * Orders rows in descending order.
   *
   * @return Ordering term to be used in `orderBy` method.
   */
  @CheckResult
  fun desc(): OrderingTerm = OrderingTerm(null, this, DESC)

  /**
   * Negate this expression.
   *
   * @return Negated expression
   */
  @CheckResult
  operator fun not(): Expr = UnaryExpr("NOT", this)

  /**
   * Combine this expression with another one using the AND operator.
   *
   * @param expr Expression to combine with
   * @return Combined expression
   */
  @CheckResult
  fun and(expr: Expr): Expr = BinaryExpr(" AND ", this, expr)

  /**
   * Combine this expression with another one using the AND NOT operator combination.
   *
   * @param expr Expression to combine with
   * @return Combined expression
   */
  @CheckResult
  fun andNot(expr: Expr): Expr = BinaryExpr(" AND NOT ", this, expr)

  /**
   * Combine this expression with another one using the OR operator.
   *
   * @param expr Expression to combine with
   * @return Combined expression
   */
  @CheckResult
  fun or(expr: Expr): Expr = BinaryExpr(" OR ", this, expr)

  /**
   * Combine this expression with another one using the OR NOT operator.
   *
   * @param expr Expression to combine with
   * @return Combined expression
   */
  @CheckResult
  fun orNot(expr: Expr): Expr = BinaryExpr(" OR NOT ", this, expr)
}
