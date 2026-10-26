@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * SQL row value.
 *
 * Row values are compared element by element, in order, which is useful for keyset pagination. Row values require
 * SQLite 3.15.0 or newer.
 *
 * Example:
 * ```
 * row(BOOK.TITLE, BOOK.ID) GREATER_THAN row("Kotlin", 42L)
 * ```
 *
 * @see [SQLite documentation: Row Values](https://www.sqlite.org/rowvalue.html)
 */
class RowValue internal constructor(private val node: ExpressionNode.Row) {
  /**
   * This row value > row value.
   *
   * @param other The row value to test against this row value
   * @return Expression
   */
  infix fun GREATER_THAN(other: RowValue) = binaryPredicate(
    left = node,
    operator = SqlOperator.GREATER,
    right = other.node
  )

  /**
   * This row value >= row value.
   *
   * @param other The row value to test against this row value
   * @return Expression
   */
  infix fun GREATER_OR_EQUAL(other: RowValue) = binaryPredicate(
    left = node,
    operator = SqlOperator.GREATER_OR_EQUAL,
    right = other.node
  )

  /**
   * This row value < row value.
   *
   * @param other The row value to test against this row value
   * @return Expression
   */
  infix fun LESS_THAN(other: RowValue) = binaryPredicate(
    left = node,
    operator = SqlOperator.LESS,
    right = other.node
  )

  /**
   * This row value <= row value.
   *
   * @param other The row value to test against this row value
   * @return Expression
   */
  infix fun LESS_OR_EQUAL(other: RowValue) = binaryPredicate(
    left = node,
    operator = SqlOperator.LESS_OR_EQUAL,
    right = other.node
  )
}

/**
 * Create SQL row value.
 *
 * Elements are expressions, `null` or values of type `String`, `Long`, `Int`, `Short`, `Double`, `Float`, `Boolean`,
 * `Byte` or `ByteArray`. Values of other types need `asValue`.
 *
 * @param first The first element
 * @param second The second element
 * @param remaining Additional elements
 * @return Row value
 * @throws IllegalArgumentException if an element is a query or a value of another type
 * @see [SQLite documentation: Row Values](https://www.sqlite.org/rowvalue.html)
 */
fun row(first: Any?, second: Any?, vararg remaining: Any?) = RowValue(
  ExpressionNode.Row(
    buildList(remaining.size + 2) {
      add(operandNode(first))
      add(operandNode(second))
      remaining.mapTo(
        destination = this,
        transform = ::operandNode
      )
    }
  )
)
