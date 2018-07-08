package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.Select.Select1;
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode;
import com.siimkinks.sqlitemagic.Utils.ValueParser;

import static com.siimkinks.sqlitemagic.Table.ANONYMOUS_TABLE;
import static com.siimkinks.sqlitemagic.Utils.DOUBLE_PARSER;
import static com.siimkinks.sqlitemagic.Utils.numericConstantToSqlString;

/**
 * A numeric column used in queries and conditions.
 *
 * @param <T>  Exact type
 * @param <R>  Return type (when this column is queried)
 * @param <ET> Equivalent type
 * @param <P>  Parent table type
 * @param <N>  Column nullability
 */
public class NumericColumn<T, R, ET, P, N> extends Column<T, R, ET, P, N> {
  NumericColumn(@NonNull Table<P> table, @NonNull String name, boolean allFromTable,
                @NonNull ValueParser<?> valueParser, boolean nullable, @android.support.annotation.Nullable String alias,
                @NonNull String nameInQuery) {
    super(table, name, allFromTable, valueParser, nullable, alias, nameInQuery);
  }

  NumericColumn(@NonNull Table<P> table, @NonNull String name, boolean allFromTable,
                @NonNull ValueParser<?> valueParser, boolean nullable, @android.support.annotation.Nullable String alias) {
    super(table, name, allFromTable, valueParser, nullable, alias);
  }

  @NonNull
  @Override
  public NumericColumn<T, R, ET, P, N> as(@NonNull String alias) {
    return new NumericColumn<>(table, name, allFromTable, valueParser, nullable, alias);
  }

  @NonNull
  @Override
  public <NewTableType> NumericColumn<T, R, ET, NewTableType, N> inTable(@NonNull Table<NewTableType> table) {
    return new NumericColumn<>(table, name, allFromTable, valueParser, nullable, alias);
  }

  /**
   * This column &gt; value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr greaterThan(@NonNull T value) {
    return new Expr1(this, ">?", toSqlArg(value));
  }

  /**
   * This column &gt; column.
   *
   * @param column The column to test against this column
   * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
   *               type to this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Expr greaterThan(@NonNull C column) {
    return new ExprC(this, ">", column);
  }

  /**
   * This column &gt; (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr greaterThan(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, ">", select);
  }

  /**
   * This column &gt;= value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr greaterOrEqual(@NonNull T value) {
    return new Expr1(this, ">=?", toSqlArg(value));
  }

  /**
   * This column &gt;= column.
   *
   * @param column The column to test against this column
   * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
   *               type to this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Expr greaterOrEqual(@NonNull C column) {
    return new ExprC(this, ">=", column);
  }

  /**
   * This column &gt;= (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr greaterOrEqual(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, ">=", select);
  }

  /**
   * This column &lt; value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr lessThan(@NonNull T value) {
    return new Expr1(this, "<?", toSqlArg(value));
  }

  /**
   * This column &lt; column.
   *
   * @param column The column to test against this column
   * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
   *               type to this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Expr lessThan(@NonNull C column) {
    return new ExprC(this, "<", column);
  }

  /**
   * This column &lt; (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr lessThan(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, "<", select);
  }

  /**
   * This column &lt;= value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr lessOrEqual(@NonNull T value) {
    return new Expr1(this, "<=?", toSqlArg(value));
  }

  /**
   * This column &lt;= column.
   *
   * @param column The column to test against this column
   * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
   *               type to this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Expr lessOrEqual(@NonNull C column) {
    return new ExprC(this, "<=", column);
  }

  /**
   * This column &lt;= (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr lessOrEqual(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, "<=", select);
  }

  /**
   * Create a new builder for SQL BETWEEN operator.
   *
   * @param value The first param of the BETWEEN operator
   * @return Between operator builder
   */
  @NonNull
  @CheckResult
  public final Between<T, ET> between(@NonNull T value) {
    return new Between<>(this, value, null, false);
  }

  /**
   * Create a new builder for SQL BETWEEN operator.
   *
   * @param column The first param of the BETWEEN operator
   * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
   *               type to this column
   * @return Between operator builder
   */
  @NonNull
  @CheckResult
  public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Between<T, ET> between(@NonNull C column) {
    return new Between<>(this, null, column, false);
  }

  /**
   * Create a new builder for SQL "NOT BETWEEN" operator combination.
   *
   * @param value The first param of the BETWEEN operator
   * @return Between operator builder
   */
  @NonNull
  @CheckResult
  public final Between<T, ET> notBetween(@NonNull T value) {
    return new Between<>(this, value, null, true);
  }

  /**
   * Create a new builder for SQL "NOT BETWEEN" operator combination.
   *
   * @param column The first param of the BETWEEN operator
   * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
   *               type to this column
   * @return Between operator builder
   */
  @NonNull
  @CheckResult
  public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Between<T, ET> notBetween(@NonNull C column) {
    return new Between<>(this, null, column, true);
  }

  public static final class Between<T, ET> {
    @NonNull
    final Column<T, ?, ?, ?, ?> column;
    @android.support.annotation.Nullable
    final T firstVal;
    @android.support.annotation.Nullable
    final Column<?, ?, ?, ?, ?> firstColumn;
    final boolean not;

    Between(@NonNull Column<T, ?, ?, ?, ?> column,
            @android.support.annotation.Nullable T firstVal,
            @android.support.annotation.Nullable Column<?, ?, ?, ?, ?> firstColumn,
            boolean not) {
      this.column = column;
      this.firstVal = firstVal;
      this.firstColumn = firstColumn;
      this.not = not;
    }

    /**
     * Create {@link Expr} object for the buildable BETWEEN operator.
     *
     * @param value The second param of the BETWEEN operator
     * @return Expression
     */
    @NonNull
    @CheckResult
    public final Expr and(@NonNull T value) {
      final Column<T, ?, ?, ?, ?> column = this.column;
      return new BetweenExpr(column, firstVal != null ? column.toSqlArg(firstVal) : null,
          column.toSqlArg(value), firstColumn, null, not);
    }

    /**
     * Create {@link Expr} object for the buildable BETWEEN operator.
     *
     * @param column The second param of the BETWEEN operator
     * @param <C>    Column type that extends {@link NumericColumn} and has equivalent
     *               type to this column
     * @return Expression
     */
    @NonNull
    @CheckResult
    public final <C extends NumericColumn<?, ?, ? extends ET, ?, ?>> Expr and(@NonNull C column) {
      final Column<T, ?, ?, ?, ?> baseColumn = this.column;
      return new BetweenExpr(baseColumn, firstVal != null ? baseColumn.toSqlArg(firstVal) : null,
          null, firstColumn, column, not);
    }
  }

  /**
   * This -column.
   *
   * @return Column that is the result of changing this column positive value to negative or vice versa.
   */
  @NonNull
  @CheckResult
  public final NumericColumn<T, R, ET, P, N> unaryMinus() {
    return new FunctionCopyColumn<>(table.internalAlias(""), this, "-(", ')', nullable, null);
  }

  /**
   * This column + column.
   *
   * @param column The column to add to this column
   * @param <X>    Column type that extends {@link NumericColumn}
   * @return Column that is the result of adding this column to provided column
   */
  @NonNull
  @CheckResult
  public final <X extends NumericColumn<?, ?, ? extends Number, ?, ?>> NumericColumn<Double, Double, Number, ?, Nullable> add(@NonNull X column) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, new Column[]{this, column}, "(", "+", ")", DOUBLE_PARSER, nullable || column.nullable, null);
  }

  /**
   * This column + value.
   *
   * @param value The value to add to this column
   * @return Column that is the result of adding this column to provided value
   */
  @NonNull
  @CheckResult
  public final NumericColumn<Double, Double, Number, P, N> add(@NonNull T value) {
    return new FunctionColumn<>(table.internalAlias(""), this, "(", '+' + numericConstantToSqlString(((Number) value)) + ')', DOUBLE_PARSER, nullable, null);
  }

  /**
   * This column - column.
   *
   * @param column The column to subtract from this column
   * @param <X>    Column type that extends {@link NumericColumn}
   * @return Column that is the result of subtracting provided column from this column
   */
  @NonNull
  @CheckResult
  public final <X extends NumericColumn<?, ?, ? extends Number, ?, ?>> NumericColumn<Double, Double, Number, ?, Nullable> sub(@NonNull X column) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, new Column[]{this, column}, "(", "-", ")", DOUBLE_PARSER, nullable || column.nullable, null);
  }

  /**
   * This column - value.
   *
   * @param value The value to subtract from this column
   * @return Column that is the result of subtracting provided value from this column
   */
  @NonNull
  @CheckResult
  public final NumericColumn<Double, Double, Number, P, N> sub(@NonNull T value) {
    return new FunctionColumn<>(table.internalAlias(""), this, "(", "-" + numericConstantToSqlString(((Number) value)) + ")", DOUBLE_PARSER, nullable, null);
  }

  /**
   * This column * column.
   *
   * @param column The column to multiply with this column
   * @param <X>    Column type that extends {@link NumericColumn}
   * @return Column that is the result of multiplying this column with the provided column
   */
  @NonNull
  @CheckResult
  public final <X extends NumericColumn<?, ?, ? extends Number, ?, ?>> NumericColumn<Double, Double, Number, ?, Nullable> mul(@NonNull X column) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, new Column[]{this, column}, "(", "*", ")", DOUBLE_PARSER, nullable || column.nullable, null);
  }

  /**
   * This column * value.
   *
   * @param value The value to multiply with this column
   * @return Column that is the result of multiplying this column with the provided value
   */
  @NonNull
  @CheckResult
  public final NumericColumn<Double, Double, Number, P, N> mul(@NonNull T value) {
    return new FunctionColumn<>(table.internalAlias(""), this, "(", "*" + numericConstantToSqlString(((Number) value)) + ")", DOUBLE_PARSER, nullable, null);
  }

  /**
   * This column / column.
   *
   * @param column The column to divide by
   * @param <X>    Column type that extends {@link NumericColumn}
   * @return Column that is the result of this column divided by the provided column
   */
  @NonNull
  @CheckResult
  public final <X extends NumericColumn<?, ?, ? extends Number, ?, ?>> NumericColumn<Double, Double, Number, ?, Nullable> div(@NonNull X column) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, new Column[]{this, column}, "(", "/", ")", DOUBLE_PARSER, nullable || column.nullable, null);
  }

  /**
   * This column / value.
   *
   * @param value The value to divide by
   * @return Column that is the result of this column divided by the provided value
   */
  @NonNull
  @CheckResult
  public final NumericColumn<Double, Double, Number, P, N> div(@NonNull T value) {
    return new FunctionColumn<>(table.internalAlias(""), this, "(", "/" + numericConstantToSqlString(((Number) value)) + ")", DOUBLE_PARSER, nullable, null);
  }

  /**
   * This column % column.
   *
   * @param column The column to use as the second operand of modulo
   * @param <X>    Column type that extends {@link NumericColumn}
   * @return Column that is the result of this column modulo provided column
   */
  @NonNull
  @CheckResult
  public final <X extends NumericColumn<?, ?, ? extends Number, ?, ?>> NumericColumn<Double, Double, Number, ?, Nullable> mod(@NonNull X column) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, new Column[]{this, column}, "(", "%", ")", DOUBLE_PARSER, nullable || column.nullable, null);
  }

  /**
   * This column % value.
   *
   * @param value The value to use as the second operand of modulo
   * @return Column that is the result of this column modulo provided value
   */
  @NonNull
  @CheckResult
  public final NumericColumn<Double, Double, Number, P, N> mod(@NonNull T value) {
    return new FunctionColumn<>(table.internalAlias(""), this, "(", "%" + numericConstantToSqlString(((Number) value)) + ")", DOUBLE_PARSER, nullable, null);
  }
}
