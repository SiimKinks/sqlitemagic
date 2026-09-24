package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.Utils.DOUBLE_PARSER
import com.siimkinks.sqlitemagic.Utils.ValueParser
import com.siimkinks.sqlitemagic.Utils.numericConstantToSqlString

/**
 * A numeric column used in queries and conditions.
 *
 * @param T Exact type
 * @param R Return type (when this column is queried)
 * @param ET Equivalent type
 * @param P Parent table type
 * @param N Column nullability
 */
open class NumericColumn<T, R, ET, P, N>(
  table: Table<P>,
  name: String,
  allFromTable: Boolean = false,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?,
  nameInQuery: String = when {
    table.nameInQuery.isEmpty() -> name
    else -> "${table.nameInQuery}.$name"
  },
  valueAdapter: ColumnValueAdapter<T>? = null
) : Column<T, R, ET, P, N>(
  table = table,
  name = name,
  allFromTable = allFromTable,
  valueParser = valueParser,
  nullable = nullable,
  alias = alias,
  valueAdapter = valueAdapter,
  nameInQuery = nameInQuery
) {
  protected constructor(
    source: NumericColumn<T, R, ET, P, N>,
    alias: String
  ) : this(
    table = source.table,
    name = source.name,
    allFromTable = source.allFromTable,
    valueParser = source.valueParser,
    nullable = source.nullable,
    alias = alias,
    valueAdapter = source.valueAdapter,
    nameInQuery = source.nameInQuery
  )

  @CheckResult
  override fun `as`(alias: String): NumericColumn<T, R, ET, P, N> = NumericColumn(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    valueAdapter = valueAdapter
  )

  @CheckResult
  override fun <NewTableType> inTable(
    table: Table<NewTableType>
  ): NumericColumn<T, R, ET, NewTableType, N> = NumericColumn(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    valueAdapter = valueAdapter
  )

  /**
   * This column > value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @CheckResult
  fun greaterThan(value: T & Any): Expr = Expr1(this, ">?", toSqlArg(value))

  /**
   * This column > column.
   *
   * @param column The column to test against this column
   * @param C Column type that extends [NumericColumn] and has equivalent type to this column
   * @return Expression
   */
  @CheckResult
  fun <C : NumericColumn<*, *, out ET, *, *>> greaterThan(column: C): Expr = ExprC(this, ">", column)

  /**
   * This column > (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun greaterThan(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, ">", select)

  /**
   * This column >= value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @CheckResult
  fun greaterOrEqual(value: T & Any): Expr = Expr1(this, ">=?", toSqlArg(value))

  /**
   * This column >= column.
   *
   * @param column The column to test against this column
   * @param C Column type that extends [NumericColumn] and has equivalent type to this column
   * @return Expression
   */
  @CheckResult
  fun <C : NumericColumn<*, *, out ET, *, *>> greaterOrEqual(column: C): Expr = ExprC(this, ">=", column)

  /**
   * This column >= (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun greaterOrEqual(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, ">=", select)

  /**
   * This column < value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @CheckResult
  fun lessThan(value: T & Any): Expr = Expr1(this, "<?", toSqlArg(value))

  /**
   * This column < column.
   *
   * @param column The column to test against this column
   * @param C Column type that extends [NumericColumn] and has equivalent type to this column
   * @return Expression
   */
  @CheckResult
  fun <C : NumericColumn<*, *, out ET, *, *>> lessThan(column: C): Expr = ExprC(this, "<", column)

  /**
   * This column < (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun lessThan(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, "<", select)

  /**
   * This column <= value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @CheckResult
  fun lessOrEqual(value: T & Any): Expr = Expr1(this, "<=?", toSqlArg(value))

  /**
   * This column <= column.
   *
   * @param column The column to test against this column
   * @param C Column type that extends [NumericColumn] and has equivalent type to this column
   * @return Expression
   */
  @CheckResult
  fun <C : NumericColumn<*, *, out ET, *, *>> lessOrEqual(column: C): Expr = ExprC(this, "<=", column)

  /**
   * This column <= (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun lessOrEqual(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, "<=", select)

  /**
   * Create a new builder for SQL BETWEEN operator.
   *
   * @param value The first param of the BETWEEN operator
   * @return Between operator builder
   */
  @CheckResult
  fun between(value: T & Any): Between<T, ET> = Between(this, value, null, false)

  /**
   * Create a new builder for SQL BETWEEN operator.
   *
   * @param column The first param of the BETWEEN operator
   * @param C Column type that extends [NumericColumn] and has equivalent type to this column
   * @return Between operator builder
   */
  @CheckResult
  fun <C : NumericColumn<*, *, out ET, *, *>> between(column: C): Between<T, ET> = Between(this, null, column, false)

  /**
   * Create a new builder for SQL "NOT BETWEEN" operator combination.
   *
   * @param value The first param of the BETWEEN operator
   * @return Between operator builder
   */
  @CheckResult
  fun notBetween(value: T & Any): Between<T, ET> = Between(this, value, null, true)

  /**
   * Create a new builder for SQL "NOT BETWEEN" operator combination.
   *
   * @param column The first param of the BETWEEN operator
   * @param C Column type that extends [NumericColumn] and has equivalent type to this column
   * @return Between operator builder
   */
  @CheckResult
  fun <C : NumericColumn<*, *, out ET, *, *>> notBetween(column: C): Between<T, ET> = Between(this, null, column, true)

  class Between<T, ET> internal constructor(
    internal val column: Column<T, *, *, *, *>,
    internal val firstVal: T?,
    internal val firstColumn: Column<*, *, *, *, *>?,
    internal val not: Boolean
  ) {
    /**
     * Create [Expr] object for the buildable BETWEEN operator.
     *
     * @param value The second param of the BETWEEN operator
     * @return Expression
     */
    @CheckResult
    fun and(value: T & Any): Expr = BetweenExpr(
      column = column,
      firstVal = firstVal?.let(column::toSqlArg),
      secondVal = column.toSqlArg(value),
      firstColumn = firstColumn,
      secondColumn = null,
      not = not
    )

    /**
     * Create [Expr] object for the buildable BETWEEN operator.
     *
     * @param column The second param of the BETWEEN operator
     * @param C Column type that extends [NumericColumn] and has equivalent type to this column
     * @return Expression
     */
    @CheckResult
    fun <C : NumericColumn<*, *, out ET, *, *>> and(column: C): Expr {
      val baseColumn = this.column
      return BetweenExpr(
        column = baseColumn,
        firstVal = firstVal?.let(baseColumn::toSqlArg),
        secondVal = null,
        firstColumn = firstColumn,
        secondColumn = column,
        not = not
      )
    }
  }

  /**
   * This -column.
   *
   * @return Column that is the result of changing this column positive value to negative or vice versa.
   */
  @CheckResult
  operator fun unaryMinus(): NumericColumn<T, R, ET, P, N> = FunctionCopyColumn(
    table = table.internalAlias(""),
    wrappedColumn = this,
    prefix = "-(",
    suffix = ')',
    nullable = nullable,
    alias = null
  )

  /**
   * This column + column.
   *
   * @param column The column to add to this column
   * @param X Column type that extends [NumericColumn]
   * @return Column that is the result of adding this column to provided column
   */
  @CheckResult
  operator fun <X : NumericColumn<*, *, out Number, *, *>> plus(
    column: X
  ): NumericColumn<Double, Double, Number, *, Nullable> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumns = arrayOf(this, column),
    prefix = "(",
    separator = "+",
    suffix = ")",
    valueParser = DOUBLE_PARSER,
    nullable = nullable || column.nullable,
    alias = null
  )

  /**
   * This column + value.
   *
   * @param value The value to add to this column
   * @return Column that is the result of adding this column to provided value
   */
  @CheckResult
  operator fun plus(value: T & Any): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
    table = table.internalAlias(""),
    wrappedColumn = this,
    prefix = "(",
    suffix = "+${numericConstantToSqlString(value as Number)})",
    valueParser = DOUBLE_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * This column - column.
   *
   * @param column The column to subtract from this column
   * @param X Column type that extends [NumericColumn]
   * @return Column that is the result of subtracting provided column from this column
   */
  @CheckResult
  operator fun <X : NumericColumn<*, *, out Number, *, *>> minus(
    column: X
  ): NumericColumn<Double, Double, Number, *, Nullable> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumns = arrayOf(this, column),
    prefix = "(",
    separator = "-",
    suffix = ")",
    valueParser = DOUBLE_PARSER,
    nullable = nullable || column.nullable,
    alias = null
  )

  /**
   * This column - value.
   *
   * @param value The value to subtract from this column
   * @return Column that is the result of subtracting provided value from this column
   */
  @CheckResult
  operator fun minus(value: T & Any): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
    table = table.internalAlias(""),
    wrappedColumn = this,
    prefix = "(",
    suffix = "-${numericConstantToSqlString(value as Number)})",
    valueParser = DOUBLE_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * This column * column.
   *
   * @param column The column to multiply with this column
   * @param X Column type that extends [NumericColumn]
   * @return Column that is the result of multiplying this column with provided column
   */
  @CheckResult
  operator fun <X : NumericColumn<*, *, out Number, *, *>> times(
    column: X
  ): NumericColumn<Double, Double, Number, *, Nullable> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumns = arrayOf(this, column),
    prefix = "(",
    separator = "*",
    suffix = ")",
    valueParser = DOUBLE_PARSER,
    nullable = nullable || column.nullable,
    alias = null
  )

  /**
   * This column * value.
   *
   * @param value The value to multiply with this column
   * @return Column that is the result of multiplying this column with provided value
   */
  @CheckResult
  operator fun times(value: T & Any): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
    table = table.internalAlias(""),
    wrappedColumn = this,
    prefix = "(",
    suffix = "*${numericConstantToSqlString(value as Number)})",
    valueParser = DOUBLE_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * This column / column.
   *
   * @param column The column to divide by
   * @param X Column type that extends [NumericColumn]
   * @return Column that is the result of dividing this column by provided column
   */
  @CheckResult
  operator fun <X : NumericColumn<*, *, out Number, *, *>> div(
    column: X
  ): NumericColumn<Double, Double, Number, *, Nullable> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumns = arrayOf(this, column),
    prefix = "(",
    separator = "/",
    suffix = ")",
    valueParser = DOUBLE_PARSER,
    nullable = nullable || column.nullable,
    alias = null
  )

  /**
   * This column / value.
   *
   * @param value The value to divide by
   * @return Column that is the result of dividing this column by provided value
   */
  @CheckResult
  operator fun div(value: T & Any): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
    table = table.internalAlias(""),
    wrappedColumn = this,
    prefix = "(",
    suffix = "/${numericConstantToSqlString(value as Number)})",
    valueParser = DOUBLE_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * This column % column.
   *
   * @param column The column to use as the second operand of modulo
   * @param X Column type that extends [NumericColumn]
   * @return Column that is the result of this column modulo provided column
   */
  @CheckResult
  operator fun <X : NumericColumn<*, *, out Number, *, *>> rem(
    column: X
  ): NumericColumn<Double, Double, Number, *, Nullable> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumns = arrayOf(this, column),
    prefix = "(",
    separator = "%",
    suffix = ")",
    valueParser = DOUBLE_PARSER,
    nullable = nullable || column.nullable,
    alias = null
  )

  /**
   * This column % value.
   *
   * @param value The value to use as the second operand of modulo
   * @return Column that is the result of this column modulo provided value
   */
  @CheckResult
  operator fun rem(value: T & Any): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
    table = table.internalAlias(""),
    wrappedColumn = this,
    prefix = "(",
    suffix = "%${numericConstantToSqlString(value as Number)})",
    valueParser = DOUBLE_PARSER,
    nullable = nullable,
    alias = null
  )
}
