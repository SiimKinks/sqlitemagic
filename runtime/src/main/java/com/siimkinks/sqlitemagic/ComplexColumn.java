package com.siimkinks.sqlitemagic;

import android.database.SQLException;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.siimkinks.sqlitemagic.Utils.ValueParser;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A column used in queries and conditions.
 *
 * @param <T>  Exact type
 * @param <R>  Return type (when this column is queried)
 * @param <ET> Equivalent type
 * @param <P>  Parent table type
 */
class ComplexColumn<T, R, ET, P> extends NumericColumn<T, R, ET, P> {
  ComplexColumn(@NonNull Table<P> table, @NonNull String name, boolean allFromTable,
                @NonNull ValueParser<?> valueParser, boolean nullable, @Nullable String alias) {
    super(table, name, allFromTable, valueParser, nullable, alias);
  }

  /**
   * This column = value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr is(long value) {
    return new Expr1(this, "=?", Long.toString(value));
  }

  /**
   * This column != value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr isNot(long value) {
    return new Expr1(this, "!=?", Long.toString(value));
  }

  /**
   * Create an expression to check this column against several values.
   * <p>
   * SQL: this IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr in(@NonNull @Size(min = 1) long... values) {
    final int length = values.length;
    if (length == 0) {
      throw new SQLException("Empty IN clause values");
    }
    final String[] args = new String[length];
    final StringBuilder sb = new StringBuilder(6 + (length << 1));
    sb.append(" IN (");
    for (int i = 0; i < length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('?');
      args[i] = Long.toString(values[i]);
    }
    sb.append(')');
    return new ExprN(this, sb.toString(), args);
  }

  /**
   * Create an expression to check this column against several values.
   * <p>
   * SQL: this IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr in(@NonNull Iterable<Long> values) {
    final Iterator<Long> iterator = values.iterator();
    if (!iterator.hasNext()) {
      throw new SQLException("Empty IN clause values");
    }
    final ArrayList<String> args = new ArrayList<>();
    final StringBuilder sb = new StringBuilder();
    sb.append(" IN (");

    boolean first = true;
    while (iterator.hasNext()) {
      if (first) {
        first = false;
      } else {
        sb.append(',');
      }
      sb.append('?');
      args.add(iterator.next().toString());
    }
    sb.append(')');
    return new ExprN(this, sb.toString(), args.toArray(new String[args.size()]));
  }

  /**
   * Create an expression to check this column against several values.
   * <p>
   * SQL: this NOT IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr notIn(@NonNull @Size(min = 1) long... values) {
    final int length = values.length;
    if (length == 0) {
      throw new SQLException("Empty IN clause values");
    }
    final String[] args = new String[length];
    final StringBuilder sb = new StringBuilder(10 + (length << 1));
    sb.append(" NOT IN (");
    for (int i = 0; i < length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('?');
      args[i] = Long.toString(values[i]);
    }
    sb.append(')');
    return new ExprN(this, sb.toString(), args);
  }

  /**
   * Create an expression to check this column against several values.
   * <p>
   * SQL: this NOT IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr notIn(@NonNull Iterable<Long> values) {
    final Iterator<Long> iterator = values.iterator();
    if (!iterator.hasNext()) {
      throw new SQLException("Empty IN clause values");
    }
    final ArrayList<String> args = new ArrayList<>();
    final StringBuilder sb = new StringBuilder();
    sb.append(" NOT IN (");

    boolean first = true;
    while (iterator.hasNext()) {
      if (first) {
        first = false;
      } else {
        sb.append(',');
      }
      sb.append('?');
      args.add(iterator.next().toString());
    }
    sb.append(')');
    return new ExprN(this, sb.toString(), args.toArray(new String[args.size()]));
  }

  /**
   * This column &gt; value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr greaterThan(long value) {
    return new Expr1(this, ">?", Long.toString(value));
  }

  /**
   * This column &gt;= value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr greaterOrEqual(long value) {
    return new Expr1(this, ">=?", Long.toString(value));
  }

  /**
   * This column &lt; value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr lessThan(long value) {
    return new Expr1(this, "<?", Long.toString(value));
  }

  /**
   * This column &lt;= value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr lessOrEqual(long value) {
    return new Expr1(this, "<=?", Long.toString(value));
  }
}
