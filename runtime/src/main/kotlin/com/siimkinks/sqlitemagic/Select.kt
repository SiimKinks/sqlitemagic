package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.Size
import com.siimkinks.sqlitemagic.GlobalConst.ERROR_NOT_INITIALIZED
import com.siimkinks.sqlitemagic.SqlUtil.quoteSqlStringLiteral
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.Utils.DOUBLE_PARSER
import com.siimkinks.sqlitemagic.Utils.LONG_PARSER
import com.siimkinks.sqlitemagic.Utils.STRING_PARSER
import com.siimkinks.sqlitemagic.Utils.numericConstantToSqlString
import com.siimkinks.sqlitemagic.Utils.parserForNumberType
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import com.siimkinks.sqlitemagic.internal.StringArraySet.BASE_SIZE
import java.util.LinkedList

/**
 * Builder for SQL SELECT statement.
 *
 * @param S Selection type -- either [Select1] or [SelectN]
 */
class Select<S> internal constructor(
  private val stmt: String
) : SelectSqlNode<S>(null) {
  /** Interface that represents single column selection */
  interface Select1

  /** Interface that represents n column selection */
  interface SelectN

  override fun appendSql(sb: StringBuilder) {
    sb.append(stmt)
  }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) = appendSql(sb)

  companion object {
    internal val ALL = emptyArray<Column<*, *, *, *, *>>()

    /**
     * Select all columns.
     *
     * Equivalent to statement SELECT * [...]
     *
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun all() = Columns(Select("SELECT"), ALL)

    /**
     * Select single column.
     *
     * @param column Column to select. This param must be one of annotation processor
     *               generated column objects that correspond to a column in a database
     *               table
     * @param R Java type that column represents
     * @param N Selection return type nullability
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <R, N> column(
      column: Column<*, R, *, *, N>
    ) = SingleColumn(Select("SELECT"), column)

    /**
     * Select multiple columns.
     *
     * @param columns Columns to select. These params must be one of annotation processor
     *                generated column objects that correspond to a column in a database
     *                table
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun columns(
      @Size(min = 1) vararg columns: Column<*, *, *, *, *>
    ) = Columns(Select("SELECT"), columns)

    /**
     * Select distinct column.
     *
     * Creates "SELECT DISTINCT * ..." query builder where duplicate rows
     * are removed from the set of result rows. For the purposes of detecting duplicate
     * rows, two NULL values are considered to be equal.
     *
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun distinct() = Columns(Select("SELECT DISTINCT"), ALL)

    /**
     * Select single distinct column.
     *
     * Creates "SELECT DISTINCT `column` ..." query builder where duplicate rows
     * are removed from the set of result rows. For the purposes of detecting duplicate
     * rows, two NULL values are considered to be equal.
     *
     * @param column Column to select. This param must be one of annotation processor
     *               generated column objects that corresponds to column in a database
     *               table
     * @param R Java type that column represents
     * @param N Selection return type nullability
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <R, N> distinct(
      column: Column<*, R, *, *, N>
    ) = SingleColumn(Select("SELECT DISTINCT"), column)

    /**
     * Select multiple distinct column.
     *
     * Creates "SELECT DISTINCT `column` ..." query builder where duplicate rows
     * are removed from the set of result rows. For the purposes of detecting duplicate
     * rows, two NULL values are considered to be equal.
     *
     * @param columns Columns to select. These params must be one of annotation processor
     *                generated column objects that corresponds to column in a database
     *                table
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun distinct(
      @Size(min = 1) vararg columns: Column<*, *, *, *, *>
    ) = Columns(Select("SELECT DISTINCT"), columns)

    /**
     * Select all columns from [table].
     *
     * Creates "SELECT * FROM `table` ..." query builder.
     *
     * @param table Table to select from. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @param T Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <T> from(
      table: Table<T>
    ) = From<T, T, SelectN, NotNullable>(all(), table)

    /**
     * Select all columns from `SELECT`.
     *
     * Creates "SELECT * FROM (SELECT... ) ..." query builder.
     *
     * @param select Subquery to select from
     * @param T Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <T> from(
      select: SelectNode<T, *, *>
    ) = From<T, T, SelectN, NotNullable>(all(), SelectionTable.from(select.selectBuilder, null))

    /**
     * Create raw SQL select statement.
     *
     * @param sql SQL SELECT statement
     * @return Raw SQL SELECT statement builder
     */
    @CheckResult
    fun raw(sql: String) = RawSelect(sql)

    //region Functions
    @Suppress("UNCHECKED_CAST")
    private val COUNT: NumericColumn<Long, Long, Number, Any, NotNullable> =
      NumericColumn(ANONYMOUS_TABLE as Table<Any>, "count(*)", false, LONG_PARSER, false, null)

    /**
     * The avg() function returns the average value of all non-NULL X within a group.
     * String and BLOB values that do not look like numbers are interpreted as 0.
     *
     * The result of avg() is always a floating point value as long as at there is at least
     * one non-NULL input even if all inputs are integers.
     * The result of avg() is NULL if and only if there are no non-NULL inputs.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : NumericColumn<*, *, out Number, P, N>> avg(
      column: X
    ): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "avg(",
      suffix = ")",
      valueParser = DOUBLE_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The avg() function returns the average value of distinct values of column X.
     * String and BLOB values that do not look like numbers are interpreted as 0.
     *
     * Duplicate elements are filtered before being passed into the aggregate function.
     *
     * The result of avg() is always a floating point value as long as at there is at least
     * one non-NULL input even if all inputs are integers.
     * The result of avg() is NULL if and only if there are no non-NULL inputs.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : NumericColumn<*, *, out Number, P, N>> avgDistinct(
      column: X
    ): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "avg(DISTINCT ",
      suffix = ")",
      valueParser = DOUBLE_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * Function returns the total number of rows in the group.
     *
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun count() = COUNT

    /**
     * The count(X) function returns a count of the number of times that X is not NULL in a group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, X : Column<*, *, *, P, *>> count(
      column: X
    ): NumericColumn<Long, Long, Number, P, NotNullable> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "count(",
      suffix = ")",
      valueParser = LONG_PARSER,
      nullable = false,
      alias = null
    )

    /**
     * The count(DISTINCT X) function returns the number of distinct values of column X.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, X : Column<*, *, *, P, *>> countDistinct(
      column: X
    ): NumericColumn<Long, Long, Number, P, NotNullable> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "count(DISTINCT ",
      suffix = ")",
      valueParser = LONG_PARSER,
      nullable = false,
      alias = null
    )

    /**
     * The group_concat() function returns a string which is the concatenation of all non-NULL values of X.
     * A comma (",") is used as the separator. The order of the concatenated elements is arbitrary.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, *, P, N>> groupConcat(
      column: X
    ): Column<String, String, CharSequence, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "group_concat(",
      suffix = ")",
      valueParser = STRING_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The group_concat() function returns a string which is the concatenation of all non-NULL values of X.
     * Parameter [separator] is used as the separator between instances of X.
     * The order of the concatenated elements is arbitrary.
     *
     * @param column Input of this aggregate function
     * @param separator Separator between instances of X
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, *, P, N>> groupConcat(
      column: X,
      separator: String
    ): Column<String, String, CharSequence, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "group_concat(",
      suffix = ",${quoteSqlStringLiteral(separator)})",
      valueParser = STRING_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The group_concat() function returns a string which is the concatenation of distinct values of column X.
     * A comma (",") is used as the separator. The order of the concatenated elements is arbitrary.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, *, P, N>> groupConcatDistinct(
      column: X
    ): Column<String, String, CharSequence, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "group_concat(DISTINCT ",
      suffix = ")",
      valueParser = STRING_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The group_concat() function returns a string which is the concatenation of distinct values of column X.
     * Parameter [separator] is used as the separator between instances of X.
     * The order of the concatenated elements is arbitrary.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, *, P, N>> groupConcatDistinct(
      column: X,
      separator: String
    ): Column<String, String, CharSequence, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "group_concat(DISTINCT ",
      suffix = ",${quoteSqlStringLiteral(separator)})",
      valueParser = STRING_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The max() aggregate function returns the maximum value of all values in the group.
     * The maximum value is the value that would be returned last in an ORDER BY on the same column.
     * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : Column<T, R, ET, P, N>> max(
      column: X
    ): Column<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "max(",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The max() aggregate function returns the maximum value of all values in the group.
     * The maximum value is the value that would be returned last in an ORDER BY on the same column.
     * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : NumericColumn<T, R, ET, P, N>> max(
      column: X
    ): NumericColumn<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "max(",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The max() aggregate function returns the maximum value of distinct values of column X.
     * The maximum value is the value that would be returned last in an ORDER BY on the same column.
     * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : Column<T, R, ET, P, N>> maxDistinct(
      column: X
    ): Column<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "max(DISTINCT ",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The max() aggregate function returns the maximum value of distinct values of column X.
     * The maximum value is the value that would be returned last in an ORDER BY on the same column.
     * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : NumericColumn<T, R, ET, P, N>> maxDistinct(
      column: X
    ): NumericColumn<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "max(DISTINCT ",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The min() aggregate function returns the minimum non-NULL value of all values in the group.
     * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
     * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : Column<T, R, ET, P, N>> min(
      column: X
    ): Column<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "min(",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The min() aggregate function returns the minimum non-NULL value of all values in the group.
     * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
     * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : NumericColumn<T, R, ET, P, N>> min(
      column: X
    ): NumericColumn<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "min(",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The min() aggregate function returns the minimum value of distinct values of column X.
     * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
     * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : Column<T, R, ET, P, N>> minDistinct(
      column: X
    ): Column<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "min(DISTINCT ",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * The min() aggregate function returns the minimum value of distinct values of column X.
     * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
     * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, T, R, ET, N, X : NumericColumn<T, R, ET, P, N>> minDistinct(
      column: X
    ): NumericColumn<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "min(DISTINCT ",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * Sum function that uses internally total() SQLite aggregate function.
     *
     * The function returns sum of all non-NULL values in the group. If there are no non-NULL input
     * rows then function returns 0.0. The result of function is always a floating point value.
     * This function never throws an integer overflow.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : NumericColumn<*, *, out Number, P, N>> sum(
      column: X
    ): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "total(",
      suffix = ")",
      valueParser = DOUBLE_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * Sum function that uses internally total() SQLite aggregate function.
     *
     * The function returns sum of distinct values of column X. If there are no non-NULL input
     * rows then function returns 0.0. The result of function is always a floating point value.
     * This function never throws an integer overflow.
     *
     * @param column Input of this aggregate function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Aggregate Functions](http://www.sqlite.org/lang_aggfunc.html)
     */
    @CheckResult
    fun <P, N, X : NumericColumn<*, *, out Number, P, N>> sumDistinct(
      column: X
    ): NumericColumn<Double, Double, Number, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "total(DISTINCT ",
      suffix = ")",
      valueParser = DOUBLE_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * Join columns.
     * This operator always evaluates to either NULL or a text value.
     *
     * @param columns Columns to concatenate
     * @return Column representing the result of this function
     * @see [SQLite documentation: Expression](http://www.sqlite.org/lang_expr.html)
     */
    @CheckResult
    fun <X : Column<*, *, *, *, *>> concat(
      @Size(min = 2) vararg columns: X
    ): Column<String, String, CharSequence, *, Nullable> = FunctionColumn(
      table = ANONYMOUS_TABLE,
      wrappedColumns = columns,
      prefix = "",
      separator = " || ",
      suffix = "",
      valueParser = STRING_PARSER,
      nullable = true,
      alias = null
    )

    /**
     * Number value as column.
     *
     * @param value Value
     * @return Column representing provided value
     */
    @CheckResult
    fun <V : Number> asColumn(value: V): NumericColumn<V, V, Number, *, NotNullable> =
      NumericColumn(ANONYMOUS_TABLE, numericConstantToSqlString(value), false, parserForNumberType(value), false, null)

    /**
     * CharSequence value as column.
     *
     * @param value Value
     * @return Column representing provided value
     */
    @CheckResult
    fun <V : CharSequence> asColumn(value: V): Column<V, V, CharSequence, *, NotNullable> = Column(
      table = ANONYMOUS_TABLE,
      name = quoteSqlStringLiteral(value),
      allFromTable = false,
      valueParser = STRING_PARSER,
      nullable = false,
      alias = null
    )

    /**
     * Value as column.
     *
     * @param value Value
     * @return Column representing provided value
     */
    @CheckResult
    fun <V : Any> asColumn(value: V): Column<V, V, V, *, NotNullable> = SqliteMagic.SingletonHolder
      .instance.database
      ?.columnForValue(value)
      ?: error(ERROR_NOT_INITIALIZED)

    /**
     * Value as raw column.
     *
     * @param value Value
     * @return Column representing provided value
     */
    @CheckResult
    fun <V : Any> asRawColumn(value: V): Column<V, V, V, *, NotNullable> = Column(
      table = ANONYMOUS_TABLE,
      name = value.toString(),
      allFromTable = false,
      valueParser = STRING_PARSER,
      nullable = false,
      alias = null
    )

    /**
     * The abs(X) function returns the absolute value of the numeric argument X.
     * Abs(X) returns NULL if X is NULL. If X is the integer -9223372036854775808 then abs(X)
     * throws an integer overflow error since there is no equivalent positive 64-bit two complement value.
     *
     * @param column Input of this function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Core Functions](http://www.sqlite.org/lang_corefunc.html)
     */
    @CheckResult
    fun <P, T, R, N, ET : Number, X : NumericColumn<T, R, ET, P, N>> abs(
      column: X
    ): NumericColumn<T, R, ET, P, N> = FunctionCopyColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "abs(",
      suffix = ')',
      nullable = column.nullable,
      alias = null
    )

    /**
     * For a string value X, the length(X) function returns the number of characters (not bytes) in
     * X prior to the first NUL character. Since SQLite strings do not normally contain NUL
     * characters, the length(X) function will usually return the total number of characters in the
     * string X. For a blob value X, length(X) returns the number of bytes in the blob.
     * If X is NULL then length(X) is NULL. If X is numeric then length(X) returns the length of a
     * string representation of X.
     *
     * @param column Input of this function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Core Functions](http://www.sqlite.org/lang_corefunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, *, P, N>> length(
      column: X
    ): NumericColumn<Long, Long, Number, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "length(",
      suffix = ")",
      valueParser = LONG_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The lower(X) function returns a copy of string X with all ASCII characters converted to lower case.
     * The default built-in lower() function works for ASCII characters only. To do case conversions
     * on non-ASCII characters, load the ICU extension.
     *
     * @param column Input of this function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Core Functions](http://www.sqlite.org/lang_corefunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, out CharSequence, P, N>> lower(
      column: X
    ): Column<String, String, CharSequence, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "lower(",
      suffix = ")",
      valueParser = STRING_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * The upper(X) function returns a copy of input string X in which all lower-case ASCII characters
     * are converted to their upper-case equivalent..
     *
     * @param column Input of this function
     * @return Column representing the result of this function
     * @see [SQLite documentation: Core Functions](http://www.sqlite.org/lang_corefunc.html)
     */
    @CheckResult
    fun <P, N, X : Column<*, *, out CharSequence, P, N>> upper(
      column: X
    ): Column<String, String, CharSequence, P, N> = FunctionColumn(
      table = column.table.internalAlias(""),
      wrappedColumn = column,
      prefix = "upper(",
      suffix = ")",
      valueParser = STRING_PARSER,
      nullable = column.nullable,
      alias = null
    )

    /**
     * Format string.
     * This operator always evaluates to either NULL or a text value.
     *
     * @param format Format string that specifies how to construct the output string using values taken from subsequent
     *     arguments
     * @param columns Columns as arguments to [format]
     * @return Column representing the result of this function
     * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#format)
     */
    @CheckResult
    fun <X : Column<*, *, *, *, *>> format(
      format: String,
      @Size(min = 1) vararg columns: X
    ): Column<String, String, CharSequence, *, Nullable> = FunctionColumn(
      table = ANONYMOUS_TABLE,
      wrappedColumns = columns,
      prefix = "printf(${quoteSqlStringLiteral(format)}, ",
      separator = ", ",
      suffix = ")",
      valueParser = STRING_PARSER,
      nullable = true,
      alias = null
    )
    //endregion
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param R Selection return type
   * @param N Selection return type nullability
   */
  class SingleColumn<R, N> internal constructor(
    parent: SelectSqlNode<Select1>?,
    internal val column: Column<*, R, *, *, N>
  ) : SelectSqlNode<Select1>(parent) {
    init {
      selectBuilder.columnNode = this
      // deep, so we could select any column
      selectBuilder.deep = true
      column.addArgs(selectBuilder.args)
      column.addObservedTables(selectBuilder.observedTables)
    }

    fun preCompileColumns() = StringArraySet(BASE_SIZE)
      .also(column::addSelectedTables)

    override fun appendSql(sb: StringBuilder) {
      column.appendSql(sb)
      column.appendAliasDeclarationIfNeeded(sb)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      column.appendSql(sb, systemRenamedTables)
      column.appendAliasDeclarationIfNeeded(sb)
    }

    /**
     * Define a FROM clause.
     *
     * @param table Table to select from. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @param T Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <T> from(
      table: Table<T>
    ) = From<T, R, Select1, N>(this, table)

    /**
     * Define a FROM clause.
     *
     * @param select Subquery to select from
     * @param T Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <T> from(
      select: SelectNode<T, *, *>
    ) = From<T, R, Select1, N>(this, SelectionTable.from(select.selectBuilder, null))
  }

  /**
   * Builder for SQL SELECT statement.
   */
  class Columns internal constructor(
    parent: SelectSqlNode<SelectN>,
    private val columns: Array<out Column<*, *, *, *, *>>
  ) : SelectSqlNode<SelectN>(parent) {
    private var compiledColumns: String? = null

    init {
      selectBuilder.columnsNode = this
      val args = selectBuilder.args
      val observedTables = selectBuilder.observedTables
      columns.forEach { column ->
        column.addArgs(args)
        column.addObservedTables(observedTables)
      }
    }

    /**
     * Compiles column before anything else is built.
     *
     * This method determines what tables are selected.
     *
     * @return Tables that are selected in the statement (determined by the selected column).
     * If null or empty then select is from all needed tables.
     */
    fun preCompileColumns(): StringArraySet? = when {
      columns.isEmpty() -> null
      else -> StringArraySet(columns.size).also { selectedTables ->
        columns.forEach { it.addSelectedTables(selectedTables) }
      }
    }

    fun compileColumns(
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>?
    ): SimpleArrayMap<String, Int> {
      val length = columns.size
      if (length == 0) {
        compiledColumns = "*"
        return SimpleArrayMap()
      }
      val columnPositions = SimpleArrayMap<String, Int>(length)
      val compiledCols = StringBuilder(length * 12)
      var columnOffset = 0
      columns.forEachIndexed { index, column ->
        if (index > 0) {
          compiledCols.append(',')
        }
        columnOffset = when {
          systemRenamedTables != null -> column.compile(
            columnPositions = columnPositions,
            compiledCols = compiledCols,
            systemRenamedTables = systemRenamedTables,
            columnOffset = columnOffset
          )
          else -> column.compile(
            columnPositions = columnPositions,
            compiledCols = compiledCols,
            columnOffset = columnOffset
          )
        }
      }
      compiledColumns = compiledCols.toString()
      return columnPositions
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append(compiledColumns)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) = appendSql(sb)

    /**
     * Define a FROM clause.
     *
     * @param table Table to select from. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @param T Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <T> from(
      table: Table<T>
    ) = From<T, T, SelectN, NotNullable>(this, table)

    /**
     * Define a FROM clause.
     *
     * @param select Subquery to select from
     * @param T Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun <T> from(
      select: SelectNode<T, *, *>
    ) = From<T, T, SelectN, NotNullable>(this, SelectionTable.from(select.selectBuilder, null))
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selected table type
   * @param R Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class From<T, R, S, N> internal constructor(
    parent: SelectSqlNode<S>,
    internal val table: Table<T>
  ) : SelectNode<R, S, N>(parent) {
    internal val joins = ArrayList<JoinClause>()

    init {
      selectBuilder.from = this
      (table as? SelectionTable<*>)?.linkWithOuterSelect(selectBuilder)
    }

    private fun addJoin(
      joinClause: JoinClause,
      operator: String
    ) = apply {
      joinClause.operator = operator
      joins += joinClause
      joinClause.addArgs(selectBuilder.args)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("FROM ")
      table.appendToSqlFromClause(sb)
      joins.forEach { join ->
        sb.append(' ')
        join.appendSql(sb)
      }
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      sb.append("FROM ")
      table.appendToSqlFromClause(sb)
      joins.forEach { join ->
        sb.append(' ')
        join.appendSql(sb, systemRenamedTables)
      }
    }

    /**
     * Join a table to the selected table using the comma (",") join-operator.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun join(table: Table<*>) = apply {
      joins += JoinClause(table, COMMA_JOIN, null)
    }

    /**
     * Join a table with ON or USING clause to the selected table using the
     * comma (",") join-operator.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   [Table.on] `ON` or [Table.using] `USING`
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun join(joinClause: JoinClause) = addJoin(
      joinClause = joinClause,
      operator = COMMA_JOIN
    )

    /**
     * LEFT JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun leftJoin(table: Table<*>) = apply {
      joins += JoinClause(table, LEFT_JOIN, null)
    }

    /**
     * LEFT JOIN a table with ON or USING clause to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   [Table.on] `ON` or [Table.using] `USING`
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun leftJoin(joinClause: JoinClause) = addJoin(
      joinClause = joinClause,
      operator = LEFT_JOIN
    )

    /**
     * LEFT OUTER JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun leftOuterJoin(table: Table<*>) = apply {
      joins += JoinClause(table, LEFT_OUTER_JOIN, null)
    }

    /**
     * LEFT OUTER JOIN a table with ON or USING clause to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   [Table.on] `ON` or [Table.using] `USING`
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun leftOuterJoin(joinClause: JoinClause) = addJoin(
      joinClause = joinClause,
      operator = LEFT_OUTER_JOIN
    )

    /**
     * INNER JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun innerJoin(table: Table<*>) = apply {
      joins += JoinClause(table, INNER_JOIN, null)
    }

    /**
     * INNER JOIN a table with ON or USING clause to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   [Table.on] `ON` or [Table.using] `USING`
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun innerJoin(joinClause: JoinClause) = addJoin(
      joinClause = joinClause,
      operator = INNER_JOIN
    )

    /**
     * CROSS JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun crossJoin(table: Table<*>) = apply {
      joins += JoinClause(table, CROSS_JOIN, null)
    }

    /**
     * CROSS JOIN a table with ON or USING clause to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   [Table.on] `ON` or [Table.using] `USING`
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun crossJoin(joinClause: JoinClause) = addJoin(
      joinClause = joinClause,
      operator = CROSS_JOIN
    )

    /**
     * NATURAL JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * If the NATURAL keyword is in the join-operator then an implicit USING clause
     * is added to the join-constraints. The implicit USING clause contains each of
     * the column names that appear in both the left and right-hand input datasets.
     * If the left and right-hand input datasets feature no common column names, then
     * the NATURAL keyword has no effect on the results of the join.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun naturalJoin(table: Table<*>) = apply {
      joins += JoinClause(table, NATURAL_JOIN, null)
    }

    /**
     * NATURAL LEFT JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * If the NATURAL keyword is in the join-operator then an implicit USING clause
     * is added to the join-constraints. The implicit USING clause contains each of
     * the column names that appear in both the left and right-hand input datasets.
     * If the left and right-hand input datasets feature no common column names, then
     * the NATURAL keyword has no effect on the results of the join.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun naturalLeftJoin(table: Table<*>) = apply {
      joins += JoinClause(table, NATURAL_LEFT_JOIN, null)
    }

    /**
     * NATURAL LEFT OUTER JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * If the NATURAL keyword is in the join-operator then an implicit USING clause
     * is added to the join-constraints. The implicit USING clause contains each of
     * the column names that appear in both the left and right-hand input datasets.
     * If the left and right-hand input datasets feature no common column names, then
     * the NATURAL keyword has no effect on the results of the join.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun naturalLeftOuterJoin(table: Table<*>) = apply {
      joins += JoinClause(table, NATURAL_LEFT_OUTER_JOIN, null)
    }

    /**
     * NATURAL INNER JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * If the NATURAL keyword is in the join-operator then an implicit USING clause
     * is added to the join-constraints. The implicit USING clause contains each of
     * the column names that appear in both the left and right-hand input datasets.
     * If the left and right-hand input datasets feature no common column names, then
     * the NATURAL keyword has no effect on the results of the join.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun naturalInnerJoin(table: Table<*>) = apply {
      joins += JoinClause(table, NATURAL_INNER_JOIN, null)
    }

    /**
     * NATURAL CROSS JOIN a table to the selected table.
     *
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * If the NATURAL keyword is in the join-operator then an implicit USING clause
     * is added to the join-constraints. The implicit USING clause contains each of
     * the column names that appear in both the left and right-hand input datasets.
     * If the left and right-hand input datasets feature no common column names, then
     * the NATURAL keyword has no effect on the results of the join.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun naturalCrossJoin(table: Table<*>) = apply {
      joins += JoinClause(table, NATURAL_CROSS_JOIN, null)
    }

    /**
     * Define a WHERE clause.
     *
     * @param expr WHERE clause expression
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun where(expr: Expr) = Where(this, expr)

    /**
     * Add a GROUP BY clause to the query.
     *
     * @param columns Columns to group selection by. These params must be one of
     *                annotation processor generated column objects that corresponds
     *                to column in a database table
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun groupBy(
      @Size(min = 1) vararg columns: Column<*, *, *, *, *>
    ) = GroupBy(this, columns)

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun orderBy(
      @Size(min = 1) vararg orderingTerms: OrderingTerm
    ) = OrderBy(this, orderingTerms)

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun limit(nrOfRows: Int) = Limit(this, nrOfRows.toString())

    companion object {
      const val LEFT_JOIN = "LEFT JOIN"
      private const val COMMA_JOIN = ","
      private const val LEFT_OUTER_JOIN = "LEFT OUTER JOIN"
      private const val INNER_JOIN = "INNER JOIN"
      private const val CROSS_JOIN = "CROSS JOIN"
      private const val NATURAL_JOIN = "NATURAL JOIN"
      private const val NATURAL_LEFT_JOIN = "NATURAL LEFT JOIN"
      private const val NATURAL_LEFT_OUTER_JOIN = "NATURAL LEFT OUTER JOIN"
      private const val NATURAL_INNER_JOIN = "NATURAL INNER JOIN"
      private const val NATURAL_CROSS_JOIN = "NATURAL CROSS JOIN"
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class Where<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    private val expr: Expr
  ) : SelectNode<T, S, N>(parent) {
    init {
      expr.addArgs(selectBuilder.args)
      expr.addObservedTables(selectBuilder.observedTables)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("WHERE ")
      expr.appendToSql(sb)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      sb.append("WHERE ")
      expr.appendToSql(sb, systemRenamedTables)
    }

    /**
     * Add a GROUP BY clause to the query.
     *
     * @param columns Columns to group selection by. These params must be one of
     *                annotation processor generated column objects that corresponds
     *                to column in a database table
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun groupBy(
      @Size(min = 1) vararg columns: Column<*, *, *, *, *>
    ) = GroupBy(this, columns)

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun orderBy(
      @Size(min = 1) vararg orderingTerms: OrderingTerm
    ) = OrderBy(this, orderingTerms)

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun limit(nrOfRows: Int) = Limit(this, nrOfRows.toString())
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class GroupBy<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    private val columns: Array<out Column<*, *, *, *, *>>
  ) : SelectNode<T, S, N>(parent) {
    override fun appendSql(sb: StringBuilder) {
      sb.append("GROUP BY ")
      columns.forEachIndexed { index, column ->
        if (index > 0) {
          sb.append(',')
        }
        column.appendSql(sb)
      }
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      sb.append("GROUP BY ")
      columns.forEachIndexed { index, column ->
        if (index > 0) {
          sb.append(',')
        }
        column.appendSql(sb, systemRenamedTables)
      }
    }

    /**
     * Add a HAVING clause to the query.
     *
     * @param expr HAVING clause expression
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun having(expr: Expr) = Having(this, expr)

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun orderBy(
      @Size(min = 1) vararg orderingTerms: OrderingTerm
    ) = OrderBy(this, orderingTerms)

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun limit(nrOfRows: Int) = Limit(this, nrOfRows.toString())
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class Having<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    private val expr: Expr
  ) : SelectNode<T, S, N>(parent) {
    init {
      expr.addArgs(selectBuilder.args)
      expr.addObservedTables(selectBuilder.observedTables)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("HAVING ")
      expr.appendToSql(sb)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      sb.append("HAVING ")
      expr.appendToSql(sb, systemRenamedTables)
    }

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun orderBy(
      @Size(min = 1) vararg orderingTerms: OrderingTerm
    ) = OrderBy(this, orderingTerms)

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun limit(nrOfRows: Int) = Limit(this, nrOfRows.toString())
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class CompoundSelect<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    private val op: String,
    private val select: SelectNode<*, S, *>
  ) : SelectNode<T, S, N>(parent) {
    init {
      selectBuilder.args.addAll(select.selectBuilder.args)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append(op)
        .append(' ')
      select.selectBuilder.appendCompiledQuery(sb, selectBuilder.observedTables)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) = appendSql(sb)

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun orderBy(
      @Size(min = 1) vararg orderingTerms: OrderingTerm
    ) = OrderBy(this, orderingTerms)

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun limit(nrOfRows: Int) = Limit(this, nrOfRows.toString())
  }

  /**
   * Object representing ORDER BY ordering term.
   */
  class OrderingTerm(
    private val column: Column<*, *, *, *, *>?,
    private val expr: Expr?,
    private val ordering: String?
  ) : SqlClause() {
    fun addArgs(args: ArrayList<String?>) {
      column?.addArgs(args)
      expr?.addArgs(args)
    }

    override fun appendSql(sb: StringBuilder) {
      when {
        column != null -> column.appendSql(sb)
        expr != null -> expr.appendToSql(sb)
        else -> throw IllegalStateException("Ordering term must have either column or expr")
      }
      ordering?.let(sb::append)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      when {
        column != null -> column.appendSql(sb, systemRenamedTables)
        expr != null -> expr.appendToSql(sb, systemRenamedTables)
        else -> throw IllegalStateException("Ordering term must have either column or expr")
      }
      ordering?.let(sb::append)
    }

    companion object {
      const val ASC = " ASC"
      const val DESC = " DESC"
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class OrderBy<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    @Size(min = 1) private val orderingTerms: Array<out OrderingTerm>
  ) : SelectNode<T, S, N>(parent) {
    init {
      orderingTerms.forEach { it.addArgs(selectBuilder.args) }
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("ORDER BY ")
      orderingTerms.forEachIndexed { index, orderingTerm ->
        if (index > 0) {
          sb.append(',')
        }
        orderingTerm.appendSql(sb)
      }
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      sb.append("ORDER BY ")
      orderingTerms.forEachIndexed { index, orderingTerm ->
        if (index > 0) {
          sb.append(',')
        }
        orderingTerm.appendSql(sb, systemRenamedTables)
      }
    }

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun limit(nrOfRows: Int) = Limit(this, nrOfRows.toString())
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class Limit<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    private val limitClause: String
  ) : SelectNode<T, S, N>(parent) {
    override fun appendSql(sb: StringBuilder) {
      sb.append("LIMIT ")
        .append(limitClause)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) = appendSql(sb)

    /**
     * Add a OFFSET clause to the query.
     *
     * If [m] evaluates to a negative value, the results are the same as if it had evaluated to zero.
     *
     * @param m Nr of omitted rows from the result set
     * @return SQL SELECT statement builder
     */
    @CheckResult
    fun offset(m: Int) = Offset(this, m.toString())
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param T Selection return type
   * @param S Selection type -- either [Select1] or [SelectN]
   * @param N Selection return type nullability
   */
  class Offset<T, S, N> internal constructor(
    parent: SelectNode<T, S, N>,
    private val offsetClause: String
  ) : SelectNode<T, S, N>(parent) {
    override fun appendSql(sb: StringBuilder) {
      sb.append("OFFSET ")
        .append(offsetClause)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) = appendSql(sb)
  }
}
