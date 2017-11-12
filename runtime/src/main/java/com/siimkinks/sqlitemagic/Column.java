package com.siimkinks.sqlitemagic;

import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Size;

import com.siimkinks.sqlitemagic.Select.OrderingTerm;
import com.siimkinks.sqlitemagic.Select.Select1;
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode;
import com.siimkinks.sqlitemagic.Utils.ValueParser;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;
import com.siimkinks.sqlitemagic.internal.StringArraySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import static com.siimkinks.sqlitemagic.Select.OrderingTerm.ASC;
import static com.siimkinks.sqlitemagic.Select.OrderingTerm.DESC;
import static com.siimkinks.sqlitemagic.Table.ANONYMOUS_TABLE;
import static com.siimkinks.sqlitemagic.Utils.STRING_PARSER;

/**
 * A column used in queries and conditions.
 *
 * @param <T>  Exact type
 * @param <R>  Return type (when this column is queried)
 * @param <ET> Equivalent type
 * @param <P>  Parent table type
 * @param <N>  Column nullability
 */
public class Column<T, R, ET, P, N> {
  @NonNull
  final Table<P> table;
  @NonNull
  final String name;
  final boolean allFromTable;
  @NonNull
  final String nameInQuery;
  @NonNull
  final ValueParser<?> valueParser;
  final boolean nullable;
  /**
   * User defined alias
   */
  @android.support.annotation.Nullable
  final String alias;

  Column(@NonNull Table<P> table, @NonNull String name, boolean allFromTable,
         @NonNull ValueParser<?> valueParser, boolean nullable, @android.support.annotation.Nullable String alias,
         @NonNull String nameInQuery) {
    this.table = table;
    this.name = name;
    this.allFromTable = allFromTable;
    this.valueParser = valueParser;
    this.nullable = nullable;
    this.alias = alias;
    this.nameInQuery = nameInQuery;
  }

  Column(@NonNull Table<P> table, @NonNull String name, boolean allFromTable,
         @NonNull ValueParser<?> valueParser, boolean nullable, @android.support.annotation.Nullable String alias) {
    this.table = table;
    this.name = name;
    this.allFromTable = allFromTable;
    this.valueParser = valueParser;
    this.nullable = nullable;
    this.alias = alias;
    final String tableNameInQuery = table.nameInQuery;
    this.nameInQuery = tableNameInQuery.isEmpty() ? name : tableNameInQuery + '.' + name;
  }

  void appendSql(@NonNull StringBuilder sb) {
    sb.append(nameInQuery);
  }

  void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    final Table<?> table = this.table;
    final LinkedList<String> aliases;
    if (table.hasAlias || (aliases = systemRenamedTables.get(table.name)) == null) {
      sb.append(nameInQuery);
    } else {
      if (aliases.size() > 1) {
        throw new SQLException("Ambiguous column " + nameInQuery + "; aliases=" + aliases);
      }
      sb.append(aliases.getFirst())
          .append('.')
          .append(name);
    }
  }

  void appendAliasDeclarationIfNeeded(@NonNull StringBuilder sb) {
    if (hasAlias()) {
      sb.append(" AS '");
      sb.append(getAppendableAlias());
      sb.append('\'');
    }
  }

  boolean hasAlias() {
    return alias != null && !alias.isEmpty() && !allFromTable;
  }

  @NonNull
  String getAppendableAlias() {
    final String alias = this.alias;
    if (alias == null) {
      throw new NullPointerException("Column alias == null");
    }
    final int dotPos = alias.lastIndexOf('.');
    if (dotPos != -1) {
      return alias.substring(dotPos + 1);
    }
    return alias;
  }

  void addSelectedTables(@NonNull StringArraySet result) {
    result.add(table.name);
  }

  void addArgs(@NonNull ArrayList<String> args) {
  }

  /**
   * This method gives columns the ability to add any extra tables that need
   * to be observed by the main query.
   *
   * @param tables Already observed tables
   */
  void addObservedTables(@NonNull ArrayList<String> tables) {
  }

  /**
   * <p>
   * Compile columns.
   * </p>
   * This method has the following responsibilities:
   * <ul>
   * <li>Must append itself to compiledCols -- formatted as appearing in SQL result column</li>
   * <li>Must add its position to columnPositions offsetting from provided columnOffset</li>
   * <li>Must return columnOffset + selected columns count</li>
   * </ul>
   */
  @CheckResult
  int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions,
              @NonNull StringBuilder compiledCols,
              int columnOffset) {
    final String compiledColumnName = nameInQuery;
    compiledCols.append(compiledColumnName);
    appendAliasDeclarationIfNeeded(compiledCols);
    if (allFromTable) {
      final Table table = this.table;
      putColumnPosition(columnPositions, table.nameInQuery, columnOffset, this);
      columnOffset += table.nrOfColumns;
    } else {
      putColumnPosition(columnPositions, compiledColumnName, columnOffset++, this);
    }
    return columnOffset;
  }

  /**
   * <p>
   * Compile columns.
   * </p>
   * This method has the following responsibilities:
   * <ul>
   * <li>Must append itself to compiledCols -- formatted as appearing in SQL result column</li>
   * <li>Must add its position to columnPositions offsetting from provided columnOffset</li>
   * <li>Must return columnOffset + selected columns count</li>
   * </ul>
   */
  @CheckResult
  int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions,
              @NonNull StringBuilder compiledCols,
              @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables,
              int columnOffset) {
    final Table table = this.table;
    final boolean fromAllColumns = allFromTable;
    final int tableColumnCount = fromAllColumns ? table.nrOfColumns : 0;
    final LinkedList<String> aliases;

    if (table.hasAlias || (aliases = systemRenamedTables.get(table.name)) == null) {
      final String compiledColumnName = nameInQuery;
      compiledCols.append(compiledColumnName);
      appendAliasDeclarationIfNeeded(compiledCols);
      if (fromAllColumns) {
        putColumnPosition(columnPositions, table.nameInQuery, columnOffset, this);
        columnOffset += tableColumnCount;
      } else {
        putColumnPosition(columnPositions, compiledColumnName, columnOffset++, this);
      }
      return columnOffset;
    }

    final String columnName = name;
    if (fromAllColumns) {
      boolean firstAlias = true;
      for (String alias : aliases) {
        if (firstAlias) {
          firstAlias = false;
        } else {
          compiledCols.append(',');
        }
        compiledCols.append(alias)
            .append('.')
            .append(columnName);
        putColumnPosition(columnPositions, alias, columnOffset, this);
        columnOffset += tableColumnCount;
      }
    } else {
      boolean firstAlias = true;
      for (String alias : aliases) {
        if (firstAlias) {
          firstAlias = false;
        } else {
          compiledCols.append(',');
        }
        final String newColumn = alias + '.' + columnName;
        compiledCols.append(newColumn);
        putColumnPosition(columnPositions, newColumn, columnOffset++, this);
      }
    }
    return columnOffset;
  }

  static void putColumnPosition(@NonNull SimpleArrayMap<String, Integer> columnPositions,
                                @android.support.annotation.Nullable String columnId,
                                int pos,
                                @NonNull Column column) {
    if (columnId != null) {
      columnPositions.put(columnId, pos);
    }
    if (column.alias != null) {
      columnPositions.put(column.alias, pos);
    }
  }

  @NonNull
  String toSqlArg(@NonNull T val) {
    return val.toString();
  }

  @SuppressWarnings("unchecked")
  @android.support.annotation.Nullable
  <V> V getFromCursor(@NonNull FastCursor cursor) {
    if (nullable && cursor.isNull(0)) {
      return null;
    }
    return (V) valueParser.parseFromCursor(cursor);
  }

  @SuppressWarnings("unchecked")
  @android.support.annotation.Nullable
  <V> V getFromStatement(@NonNull SQLiteStatement stm) {
    try {
      return (V) valueParser.parseFromStatement(stm);
    } catch (SQLiteDoneException e) {
      // query returned no results
    }
    return null;
  }

  /*
   * Makes copy that does not consider systemRenamedTables when building SQL.
   * Should only be used in SQL compiling. Copies are made with table structure column
   * constants -- we don't have to consider any internal column type here.
   * Typically used in making renamed joins.
   */
  @NonNull
  static <T, R, ET, P, N> Column<T, R, ET, P, N> internalCopy(@NonNull Table<P> newTable,
                                                              @NonNull final Column<T, R, ET, ?, N> column) {
    return new Column<T, R, ET, P, N>(newTable, column.name, column.allFromTable, column.valueParser, column.nullable, column.alias) {
      @NonNull
      @Override
      public String toSqlArg(@NonNull T val) {
        return column.toSqlArg(val);
      }

      @android.support.annotation.Nullable
      @Override
      <V> V getFromCursor(@NonNull FastCursor cursor) {
        return column.getFromCursor(cursor);
      }

      @android.support.annotation.Nullable
      @Override
      <V> V getFromStatement(@NonNull SQLiteStatement stm) {
        return column.getFromStatement(stm);
      }

      @Override
      void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
        super.appendSql(sb);
      }
    };
  }

  /**
   * Create an alias for this column.
   * <p>
   * Note that column aliases are quoted and thus case-sensitive!
   *
   * @param alias The alias name
   * @return New column with provided alias.
   */
  @NonNull
  @CheckResult
  public Column<T, R, ET, P, N> as(@NonNull String alias) {
    return new Column<>(table, name, allFromTable, valueParser, nullable, alias);
  }

  /**
   * Convert this column to not-nullable column.
   *
   * @return Non-nullable column.
   */
  @NonNull
  @CheckResult
  public Column<T, R, ET, P, NotNullable> toNotNullable() {
    if (nullable) {
      final String errorMsg = "Converting nullable column [" + this + "] to not nullable. This might cause data inconsistencies!";
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logError(errorMsg, new IllegalStateException(errorMsg));
    }
    //noinspection unchecked
    return (Column<T, R, ET, P, NotNullable>) this;
  }

  /**
   * Create ORDER BY ordering term.
   * <p>
   * Orders rows in ascending order.
   *
   * @return Ordering term to be used in {@code orderBy} method.
   */
  @NonNull
  @CheckResult
  public OrderingTerm asc() {
    return new OrderingTerm(this, null, ASC);
  }

  /**
   * Create ORDER BY ordering term.
   * <p>
   * Orders rows in descending order.
   *
   * @return Ordering term to be used in {@code orderBy} method.
   */
  @NonNull
  @CheckResult
  public OrderingTerm desc() {
    return new OrderingTerm(this, null, DESC);
  }

  /**
   * Concat column with this column.
   * This operator always evaluates to either NULL or a text value.
   *
   * @param column Column to concatenate
   * @param <X>    Column type that extends {@link Column}
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_expr.html">SQLite documentation: Expression</a>
   */
  @NonNull
  @CheckResult
  public final <X extends Column<?, ?, ?, ?, ?>> Column<String, String, CharSequence, ?, Nullable> concat(@NonNull X column) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, new Column[]{this, column}, "", " || ", "", STRING_PARSER, nullable || column.nullable, null);
  }

  /**
   * The replace(X,Y,Z) function returns a string formed by substituting string
   * Z for every occurrence of string Y in string X.
   * <p>
   * The BINARY collating sequence is used for comparisons. If Y is an empty string
   * then return X unchanged. If Z is not initially a string, it is cast to a
   * UTF-8 string prior to processing.
   *
   * @param target      The sequence of char values to be replaced
   * @param replacement The replacement sequence of char values
   * @return Column representing the result of this function
   * @see <a href="https://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public final Column<String, String, CharSequence, ?, N> replace(@NonNull CharSequence target, @NonNull CharSequence replacement) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, this, "replace(", ",'" + target + "','" + replacement + "')", STRING_PARSER, nullable, null);
  }

  /**
   * The substr(X,Y) function returns a substring of input string X that begins with
   * the Y-th character.
   * <p>
   * This function returns all characters through the end of
   * the string X beginning with the Y-th. The left-most character of X is number 1.
   * If Y is negative then the first character of the substring is found by counting
   * from the right rather than the left.
   * If X is a string then characters indices refer to actual UTF-8 characters.
   * If X is a BLOB then the indices refer to bytes.
   * <p>
   * Examples:
   * <blockquote><pre>
   * "zero".substring(0) returns "zero"
   * "first".substring(1) returns "first"
   * "unhappy".substring(3) returns "happy"
   * "Harbison".substring(4) returns "bison"
   * "emptiness".substring(10) returns "" (an empty string)
   * "last".substring(-1) returns "t"
   * "substring".substring(-6) returns "string"
   * </pre></blockquote>
   *
   * @param beginPos The begin position ({@code Y}). The first position in the string is always 1
   * @return Column representing the result of this function
   * @see <a href="https://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public final Column<String, String, CharSequence, ?, N> substring(int beginPos) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, this, "substr(", "," + beginPos + ")", STRING_PARSER, nullable, null);
  }

  /**
   * The substring(X,Y,Z) function returns a substring of input string X that begins with
   * the Y-th character and which is Z characters long.
   * <p>
   * The left-most character of X is number 1. If Y is negative then the first character
   * of the substring is found by counting from the right rather than the left.
   * If Z is negative then the abs(Z) characters preceding the Y-th character are returned.
   * If X is a string then characters indices refer to actual UTF-8 characters.
   * If X is a BLOB then the indices refer to bytes.
   * <p>
   * Examples:
   * <blockquote><pre>
   * "first".substring(1, 2) returns "fi"
   * "smiles".substring(2, 5) returns "mile"
   * "hamburger".substring(4, -3) returns "ham"
   * "last".substring(-1, -2) returns "as"
   * </pre></blockquote>
   *
   * @param beginPos      The begin position ({@code Y}). The first position in the string is always 1
   * @param charsToReturn Number of characters to be returned ({@code Z})
   * @return Column representing the result of this function
   * @see <a href="https://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public final Column<String, String, CharSequence, ?, N> substring(int beginPos, int charsToReturn) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, this, "substr(", "," + beginPos + "," + charsToReturn + ")", STRING_PARSER, nullable, null);
  }

  /**
   * The trim(X) function removes spaces from both ends of X.
   *
   * @return Column representing the result of this function
   * @see <a href="https://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public final Column<String, String, CharSequence, ?, N> trim() {
    return new FunctionColumn<>(ANONYMOUS_TABLE, this, "trim(", ")", STRING_PARSER, nullable, null);
  }

  /**
   * The trim(X,Y) function returns a string formed by removing any and all characters
   * that appear in Y from both ends of X.
   *
   * @param trimString The string that will be removed from both sides of the target column
   * @return Column representing the result of this function
   * @see <a href="https://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public final Column<String, String, CharSequence, ?, N> trim(@NonNull CharSequence trimString) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, this, "trim(", ",'" + trimString + "')", STRING_PARSER, nullable, null);
  }

  /**
   * This column = value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr is(@NonNull T value) {
    return new Expr1(this, "=?", toSqlArg(value));
  }

  /**
   * This column = column.
   *
   * @param column The column to test against this column
   * @param <C>    Column type that extends {@link Column} and has equivalent type to this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final <C extends Column<?, ?, ? extends ET, ?, ?>> Expr is(@NonNull C column) {
    return new ExprC(this, "=", column);
  }

  /**
   * This column = (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr is(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, "=", select);
  }

  /**
   * This column != value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr isNot(@NonNull T value) {
    return new Expr1(this, "!=?", toSqlArg(value));
  }

  /**
   * This column != column.
   *
   * @param column The column to test against this column
   * @param <C>    Column type that extends {@link Column} and has equivalent type to this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final <C extends Column<?, ?, ? extends ET, ?, ?>> Expr isNot(@NonNull C column) {
    return new ExprC(this, "!=", column);
  }

  /**
   * This column != (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr isNot(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, "!=", select);
  }

  /**
   * This column IS NULL.
   *
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr isNull() {
    return new Expr(this, " IS NULL");
  }

  /**
   * This column IS NOT NULL.
   *
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr isNotNull() {
    return new Expr(this, " IS NOT NULL");
  }

  /**
   * Uses the LIKE operation. Case insensitive comparisons.
   *
   * @param likeRegex Uses sqlite LIKE regex to match rows.
   *                  It must be a string to escape it properly.
   *                  There are two wildcards: % and _
   *                  % represents [0,many) numbers or characters.
   *                  The _ represents a single number or character.
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr like(@NonNull String likeRegex) {
    return new Expr1(this, " LIKE ?", likeRegex);
  }

  /**
   * Uses the LIKE operation. Case insensitive comparisons.
   *
   * @param likeRegex Uses sqlite LIKE regex to match rows.
   *                  It must be a string to escape it properly.
   *                  There are two wildcards: % and _
   *                  % represents [0,many) numbers or characters.
   *                  The _ represents a single number or character.
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr notLike(@NonNull String likeRegex) {
    return new Expr1(this, " NOT LIKE ?", likeRegex);
  }

  /**
   * Uses the GLOB operation. Similar to LIKE except it uses case sensitive comparisons.
   *
   * @param globRegex Uses sqlite GLOB regex to match rows.
   *                  It must be a string to escape it properly.
   *                  There are two wildcards: * and ?
   *                  * represents [0,many) numbers or characters.
   *                  The ? represents a single number or character
   * @return Expression
   */
  @NonNull
  @CheckResult
  public Expr glob(@NonNull String globRegex) {
    return new Expr1(this, " GLOB ?", globRegex);
  }

  /**
   * Uses the GLOB operation. Similar to LIKE except it uses case sensitive comparisons.
   *
   * @param globRegex Uses sqlite GLOB regex to match rows.
   *                  It must be a string to escape it properly.
   *                  There are two wildcards: * and ?
   *                  * represents [0,many) numbers or characters.
   *                  The ? represents a single number or character
   * @return Expression
   */
  @NonNull
  @CheckResult
  public Expr notGlob(@NonNull String globRegex) {
    return new Expr1(this, " NOT GLOB ?", globRegex);
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
  public final Expr in(@NonNull @Size(min = 1) Collection<T> values) {
    final int length = values.size();
    if (length == 0) {
      throw new SQLException("Empty IN clause values");
    }
    final String[] args = new String[length];
    final StringBuilder sb = new StringBuilder(6 + (length << 1));
    sb.append(" IN (");
    final Iterator<T> iterator = values.iterator();
    for (int i = 0; i < length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('?');
      args[i] = toSqlArg(iterator.next());
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
  @SafeVarargs
  @NonNull
  @CheckResult
  public final Expr in(@NonNull @Size(min = 1) T... values) {
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
      args[i] = toSqlArg(values[i]);
    }
    sb.append(')');
    return new ExprN(this, sb.toString(), args);
  }

  /**
   * Create an expression to check this column against a subquery.<br>
   * Note that the subquery must return exactly one column.
   * <p>
   * SQL: this IN (SELECT...)
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr in(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, " IN ", select);
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
  public final Expr notIn(@NonNull @Size(min = 1) Collection<T> values) {
    final int length = values.size();
    if (length == 0) {
      throw new SQLException("Empty IN clause values");
    }
    final String[] args = new String[length];
    final StringBuilder sb = new StringBuilder(10 + (length << 1));
    sb.append(" NOT IN (");
    final Iterator<T> iterator = values.iterator();
    for (int i = 0; i < length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('?');
      args[i] = toSqlArg(iterator.next());
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
  @SafeVarargs
  @NonNull
  @CheckResult
  public final Expr notIn(@NonNull @Size(min = 1) T... values) {
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
      args[i] = toSqlArg(values[i]);
    }
    sb.append(')');
    return new ExprN(this, sb.toString(), args);
  }

  /**
   * Create an expression to check this column against a subquery.<br>
   * Note that the subquery must return exactly one column.
   * <p>
   * SQL: this NOT IN (SELECT...)
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @NonNull
  @CheckResult
  public final Expr notIn(@NonNull SelectNode<? extends ET, Select1, ?> select) {
    return new ExprS(this, " NOT IN ", select);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;

    final Column<?, ?, ?, ?, ?> column;
    try {
      column = (Column<?, ?, ?, ?, ?>) o;
    } catch (ClassCastException e) {
      return false;
    }

    return table.baseNameEquals(column.table) && name.equals(column.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return table + "." + name;
  }
}
