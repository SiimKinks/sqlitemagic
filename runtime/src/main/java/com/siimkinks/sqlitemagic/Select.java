package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;
import com.siimkinks.sqlitemagic.internal.StringArraySet;

import java.util.ArrayList;
import java.util.LinkedList;

import static com.siimkinks.sqlitemagic.Utils.numericConstantToSqlString;
import static com.siimkinks.sqlitemagic.internal.StringArraySet.BASE_SIZE;
import static com.siimkinks.sqlitemagic.Table.ANONYMOUS_TABLE;
import static com.siimkinks.sqlitemagic.Utils.DOUBLE_PARSER;
import static com.siimkinks.sqlitemagic.Utils.LONG_PARSER;
import static com.siimkinks.sqlitemagic.Utils.STRING_PARSER;
import static com.siimkinks.sqlitemagic.Utils.parserForNumberType;

/**
 * Builder for SQL SELECT statement.
 *
 * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
 */
public final class Select<S> extends SelectSqlNode<S> {
  /**
   * Interface that represents single column selection
   */
  public interface Select1 {
  }

  /**
   * Interface that represents n column selection
   */
  public interface SelectN {
  }

  static final Column<?, ?, ?, ?, ?>[] ALL = new Column<?, ?, ?, ?, ?>[0];

  @NonNull
  private final String stmt;

  Select(@NonNull String stmt) {
    super(null);
    this.stmt = stmt;
  }

  @Override
  void appendSql(@NonNull StringBuilder sb) {
    sb.append(stmt);
  }

  @Override
  void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append(stmt);
  }

  /**
   * Select all column.
   * <p>
   * Equivalent to statement SELECT * [...]
   *
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static Columns all() {
    return new Columns(new Select<SelectN>("SELECT"), ALL);
  }

  /**
   * Select single column.
   *
   * @param column Column to select. This param must be one of annotation processor
   *               generated column objects that correspond to a column in a database
   *               table
   * @param <R>    Java type that column represents
   * @param <N>    Selection return type nullability
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static <R, N> SingleColumn<R, N> column(@NonNull Column<?, R, ?, ?, N> column) {
    return new SingleColumn<>(new Select<Select1>("SELECT"), column);
  }

  /**
   * Select multiple column.
   *
   * @param columns Columns to select. These params must be one of annotation processor
   *                generated column objects that correspond to a column in a database
   *                table
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static Columns columns(@NonNull @Size(min = 1) Column<?, ?, ?, ?, ?>... columns) {
    return new Columns(new Select<SelectN>("SELECT"), columns);
  }

  /**
   * Select distinct column.
   * <p>
   * Creates "SELECT DISTINCT * ..." query builder where duplicate rows
   * are removed from the set of result rows. For the purposes of detecting duplicate
   * rows, two NULL values are considered to be equal.
   *
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static Columns distinct() {
    return new Columns(new Select<SelectN>("SELECT DISTINCT"), ALL);
  }

  /**
   * Select single distinct column.
   * <p>
   * Creates "SELECT DISTINCT {@code column} ..." query builder where duplicate rows
   * are removed from the set of result rows. For the purposes of detecting duplicate
   * rows, two NULL values are considered to be equal.
   *
   * @param column Column to select. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database
   *               table
   * @param <R>    Java type that column represents
   * @param <N>    Selection return type nullability
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static <R, N> SingleColumn<R, N> distinct(@NonNull Column<?, R, ?, ?, N> column) {
    return new SingleColumn<>(new Select<Select1>("SELECT DISTINCT"), column);
  }

  /**
   * Select multiple distinct column.
   * <p>
   * Creates "SELECT DISTINCT {@code column} ..." query builder where duplicate rows
   * are removed from the set of result rows. For the purposes of detecting duplicate
   * rows, two NULL values are considered to be equal.
   *
   * @param columns Columns to select. These params must be one of annotation processor
   *                generated column objects that corresponds to column in a database
   *                table
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static Columns distinct(@NonNull @Size(min = 1) Column<?, ?, ?, ?, ?>... columns) {
    return new Columns(new Select<SelectN>("SELECT DISTINCT"), columns);
  }

  /**
   * Select all column from {@code table}.
   * <p>
   * Creates "SELECT * FROM {@code table} ..." query builder.
   *
   * @param table Table to select from. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @param <T>   Java type that table represents
   * @return SQL SELECT statement builder
   */
  @CheckResult
  public static <T> From<T, T, SelectN, NotNullable> from(@NonNull Table<T> table) {
    return new From<>(all(), table);
  }

  /**
   * Create raw SQL select statement.
   *
   * @param sql SQL SELECT statement
   * @return Raw SQL SELECT statement builder
   */
  @CheckResult
  public static RawSelect raw(@NonNull String sql) {
    return new RawSelect(sql);
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <R> Selection return type
   * @param <N> Selection return type nullability
   */
  public static final class SingleColumn<R, N> extends SelectSqlNode<Select1> {
    @NonNull
    final Column<?, R, ?, ?, N> column;

    SingleColumn(@Nullable SelectSqlNode<Select1> parent, @NonNull Column<?, R, ?, ?, N> column) {
      super(parent);
      this.column = column;
      selectBuilder.columnNode = this;
      // deep, so we could select any column
      selectBuilder.deep = true;
      column.addArgs(selectBuilder.args);
      column.addObservedTables(selectBuilder.observedTables);
    }

    @NonNull
    StringArraySet preCompileColumns() {
      final StringArraySet selectFromTables = new StringArraySet(BASE_SIZE);
      column.addSelectedTables(selectFromTables);
      return selectFromTables;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      column.appendSql(sb);
      column.appendAliasDeclarationIfNeeded(sb);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      column.appendSql(sb, systemRenamedTables);
      column.appendAliasDeclarationIfNeeded(sb);
    }

    /**
     * Define a FROM clause.
     *
     * @param table Table to select from. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @param <T>   Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public <T> From<T, R, Select1, N> from(@NonNull Table<T> table) {
      return new From<>(this, table);
    }
  }

  /**
   * Builder for SQL SELECT statement.
   */
  public static final class Columns extends SelectSqlNode<SelectN> {
    @NonNull
    final Column[] columns;
    String compiledColumns;

    Columns(@NonNull SelectSqlNode<SelectN> parent, @NonNull Column<?, ?, ?, ?, ?>[] columns) {
      super(parent);
      this.columns = columns;
      selectBuilder.columnsNode = this;
      final ArrayList<String> args = selectBuilder.args;
      final ArrayList<String> observedTables = selectBuilder.observedTables;
      for (int i = 0, length = columns.length; i < length; i++) {
        final Column<?, ?, ?, ?, ?> column = columns[i];
        column.addArgs(args);
        column.addObservedTables(observedTables);
      }
    }

    /**
     * Compiles column before anything else is built.
     * <p>
     * This method determines what tables are selected.
     *
     * @return Tables that are selected in the statement (determined by the selected column).
     * If null or empty then select is from all needed tables.
     */
    @Nullable
    StringArraySet preCompileColumns() {
      final Column[] columns = this.columns;
      final int columnsCount = columns.length;
      if (columnsCount > 0) {
        final StringArraySet selectFromTables = new StringArraySet(columnsCount);
        for (int i = 0; i < columnsCount; i++) {
          columns[i].addSelectedTables(selectFromTables);
        }
        return selectFromTables;
      }
      return null;
    }

    @NonNull
    SimpleArrayMap<String, Integer> compileColumns(@Nullable SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      final Column[] columns = this.columns;
      final int length = columns.length;
      if (length == 0) {
        this.compiledColumns = "*";
        return new SimpleArrayMap<>();
      }
      final SimpleArrayMap<String, Integer> columnPositions = new SimpleArrayMap<>(length);
      final StringBuilder compiledCols = new StringBuilder(length * 12);
      int columnOffset = 0;
      boolean first = true;
      for (int i = 0; i < length; i++) {
        if (first) {
          first = false;
        } else {
          compiledCols.append(',');
        }
        if (systemRenamedTables != null) {
          columnOffset = columns[i].compile(columnPositions, compiledCols, systemRenamedTables, columnOffset);
        } else {
          columnOffset = columns[i].compile(columnPositions, compiledCols, columnOffset);
        }
      }
      this.compiledColumns = compiledCols.toString();
      return columnPositions;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append(compiledColumns);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append(compiledColumns);
    }

    /**
     * Define a FROM clause.
     *
     * @param table Table to select from. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @param <T>   Java type that table represents
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public <T> From<T, T, SelectN, NotNullable> from(@NonNull Table<T> table) {
      return new From<>(this, table);
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selected table type
   * @param <R> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class From<T, R, S, N> extends SelectNode<R, S, N> {
    private static final String COMMA_JOIN = ",";
    static final String LEFT_JOIN = "LEFT JOIN";
    private static final String LEFT_OUTER_JOIN = "LEFT OUTER JOIN";
    private static final String INNER_JOIN = "INNER JOIN";
    private static final String CROSS_JOIN = "CROSS JOIN";
    private static final String NATURAL_JOIN = "NATURAL JOIN";
    private static final String NATURAL_LEFT_JOIN = "NATURAL LEFT JOIN";
    private static final String NATURAL_LEFT_OUTER_JOIN = "NATURAL LEFT OUTER JOIN";
    private static final String NATURAL_INNER_JOIN = "NATURAL INNER JOIN";
    private static final String NATURAL_CROSS_JOIN = "NATURAL CROSS JOIN";

    @NonNull
    final Table<T> table;
    final ArrayList<JoinClause> joins = new ArrayList<>();

    From(@NonNull SelectSqlNode<S> parent, @NonNull Table<T> table) {
      super(parent);
      this.table = table;
      selectBuilder.from = this;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("FROM ");
      table.appendToSqlFromClause(sb);
      final ArrayList<JoinClause> joins = this.joins;
      for (int i = 0, size = joins.size(); i < size; i++) {
        sb.append(' ');
        joins.get(i).appendSql(sb);
      }
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("FROM ");
      table.appendToSqlFromClause(sb);
      final ArrayList<JoinClause> joins = this.joins;
      for (int i = 0, size = joins.size(); i < size; i++) {
        sb.append(' ');
        joins.get(i).appendSql(sb, systemRenamedTables);
      }
    }

    /**
     * Join a table to the selected table using the comma (",") join-operator.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> join(@NonNull Table table) {
      joins.add(new JoinClause(table, COMMA_JOIN, null));
      return this;
    }

    /**
     * Join a table with ON or USING clause to the selected table using the
     * comma (",") join-operator.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   {@link Table#on(Expr) ON} or {@link Table#using(Column[]) USING}
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> join(@NonNull JoinClause joinClause) {
      joinClause.operator = COMMA_JOIN;
      joins.add(joinClause);
      joinClause.addArgs(selectBuilder.args);
      return this;
    }

    /**
     * LEFT JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> leftJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, LEFT_JOIN, null));
      return this;
    }

    /**
     * LEFT JOIN a table with ON or USING clause to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   {@link Table#on(Expr) ON} or {@link Table#using(Column[]) USING}
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> leftJoin(@NonNull JoinClause joinClause) {
      joinClause.operator = LEFT_JOIN;
      joins.add(joinClause);
      joinClause.addArgs(selectBuilder.args);
      return this;
    }

    /**
     * LEFT OUTER JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> leftOuterJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, LEFT_OUTER_JOIN, null));
      return this;
    }

    /**
     * LEFT OUTER JOIN a table with ON or USING clause to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   {@link Table#on(Expr) ON} or {@link Table#using(Column[]) USING}
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> leftOuterJoin(@NonNull JoinClause joinClause) {
      joinClause.operator = LEFT_OUTER_JOIN;
      joins.add(joinClause);
      joinClause.addArgs(selectBuilder.args);
      return this;
    }

    /**
     * INNER JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> innerJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, INNER_JOIN, null));
      return this;
    }

    /**
     * INNER JOIN a table with ON or USING clause to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   {@link Table#on(Expr) ON} or {@link Table#using(Column[]) USING}
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> innerJoin(@NonNull JoinClause joinClause) {
      joinClause.operator = INNER_JOIN;
      joins.add(joinClause);
      joinClause.addArgs(selectBuilder.args);
      return this;
    }

    /**
     * CROSS JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param table Table to join. This param must be one of annotation processor
     *              generated table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public From<T, R, S, N> crossJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, CROSS_JOIN, null));
      return this;
    }

    /**
     * CROSS JOIN a table with ON or USING clause to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     *
     * @param joinClause Join clause to use. This param is the result of
     *                   {@link Table#on(Expr) ON} or {@link Table#using(Column[]) USING}
     *                   clauses invoked on one of annotation processor generated
     *                   table objects that corresponds to table in a database
     * @return SQL SELECT statement builder
     */

    @CheckResult
    public From<T, R, S, N> crossJoin(@NonNull JoinClause joinClause) {
      joinClause.operator = CROSS_JOIN;
      joins.add(joinClause);
      joinClause.addArgs(selectBuilder.args);
      return this;
    }

    /**
     * NATURAL JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     * <p>
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
    public From<T, R, S, N> naturalJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, NATURAL_JOIN, null));
      return this;
    }

    /**
     * NATURAL LEFT JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     * <p>
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
    public From<T, R, S, N> naturalLeftJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, NATURAL_LEFT_JOIN, null));
      return this;
    }

    /**
     * NATURAL LEFT OUTER JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     * <p>
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
    public From<T, R, S, N> naturalLeftOuterJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, NATURAL_LEFT_OUTER_JOIN, null));
      return this;
    }

    /**
     * NATURAL INNER JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     * <p>
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
    public From<T, R, S, N> naturalInnerJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, NATURAL_INNER_JOIN, null));
      return this;
    }

    /**
     * NATURAL CROSS JOIN a table to the selected table.
     * <p>
     * Joined table can only be a complex column of the selected table. Any other table join
     * will be ignored in the resulting SQL.
     * <p>
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
    public From<T, R, S, N> naturalCrossJoin(@NonNull Table table) {
      joins.add(new JoinClause(table, NATURAL_CROSS_JOIN, null));
      return this;
    }

    /**
     * Define a WHERE clause.
     *
     * @param expr WHERE clause expression
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Where<R, S, N> where(@NonNull Expr expr) {
      return new Where<>(this, expr);
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
    public GroupBy<R, S, N> groupBy(@NonNull @Size(min = 1) Column... columns) {
      return new GroupBy<>(this, columns);
    }

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public OrderBy<R, S, N> orderBy(@NonNull @Size(min = 1) OrderingTerm... orderingTerms) {
      return new OrderBy<>(this, orderingTerms);
    }

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Limit<R, S, N> limit(int nrOfRows) {
      return new Limit<>(this, Integer.toString(nrOfRows));
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class Where<T, S, N> extends SelectNode<T, S, N> {
    @NonNull
    private final Expr expr;

    Where(@NonNull SelectNode<T, S, N> parent, @NonNull Expr expr) {
      super(parent);
      this.expr = expr;
      expr.addArgs(selectBuilder.args);
      expr.addObservedTables(selectBuilder.observedTables);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("WHERE ");
      expr.appendToSql(sb);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("WHERE ");
      expr.appendToSql(sb, systemRenamedTables);
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
    public GroupBy<T, S, N> groupBy(@NonNull @Size(min = 1) Column... columns) {
      return new GroupBy<>(this, columns);
    }

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public OrderBy<T, S, N> orderBy(@NonNull @Size(min = 1) OrderingTerm... orderingTerms) {
      return new OrderBy<>(this, orderingTerms);
    }

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Limit<T, S, N> limit(int nrOfRows) {
      return new Limit<>(this, Integer.toString(nrOfRows));
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class GroupBy<T, S, N> extends SelectNode<T, S, N> {
    @NonNull
    private final Column[] columns;

    GroupBy(@NonNull SelectNode<T, S, N> parent, @NonNull Column[] columns) {
      super(parent);
      this.columns = columns;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("GROUP BY ");
      appendColumns(sb, columns);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("GROUP BY ");
      appendColumns(sb, columns, systemRenamedTables);
    }

    /**
     * Add a HAVING clause to the query.
     *
     * @param expr HAVING clause expression
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Having<T, S, N> having(@NonNull Expr expr) {
      return new Having<>(this, expr);
    }

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public OrderBy<T, S, N> orderBy(@NonNull @Size(min = 1) OrderingTerm... orderingTerms) {
      return new OrderBy<>(this, orderingTerms);
    }

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Limit<T, S, N> limit(int nrOfRows) {
      return new Limit<>(this, Integer.toString(nrOfRows));
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class Having<T, S, N> extends SelectNode<T, S, N> {
    @NonNull
    private final Expr expr;

    Having(@NonNull SelectNode<T, S, N> parent, @NonNull Expr expr) {
      super(parent);
      this.expr = expr;
      expr.addArgs(selectBuilder.args);
      expr.addObservedTables(selectBuilder.observedTables);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("HAVING ");
      expr.appendToSql(sb);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("HAVING ");
      expr.appendToSql(sb, systemRenamedTables);
    }

    /**
     * Add an ORDER BY clause to the query.
     *
     * @param orderingTerms Ordering terms that define ORDER BY clause.
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public OrderBy<T, S, N> orderBy(@NonNull @Size(min = 1) OrderingTerm... orderingTerms) {
      return new OrderBy<>(this, orderingTerms);
    }

    /**
     * Add a LIMIT clause to the query.
     *
     * @param nrOfRows Upper bound on the number of rows returned by the
     *                 entire SELECT statement
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Limit<T, S, N> limit(int nrOfRows) {
      return new Limit<>(this, Integer.toString(nrOfRows));
    }
  }

  /**
   * Object representing ORDER BY ordering term.
   */
  public static final class OrderingTerm extends SqlClause {
    static final String ASC = " ASC";
    static final String DESC = " DESC";

    @Nullable
    private final Column column;
    @Nullable
    private final Expr expr;
    @Nullable
    private final String ordering;

    OrderingTerm(@Nullable Column column,
                 @Nullable Expr expr,
                 @Nullable String ordering) {
      this.column = column;
      this.expr = expr;
      this.ordering = ordering;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      if (column != null) {
        column.appendSql(sb);
      } else if (expr != null) {
        expr.appendToSql(sb);
      } else {
        throw new IllegalStateException("Ordering term must have either column or expr");
      }
      if (ordering != null) {
        sb.append(ordering);
      }
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      if (column != null) {
        column.appendSql(sb, systemRenamedTables);
      } else if (expr != null) {
        expr.appendToSql(sb, systemRenamedTables);
      } else {
        throw new IllegalStateException("Ordering term must have either column or expr");
      }
      if (ordering != null) {
        sb.append(ordering);
      }
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class OrderBy<T, S, N> extends SelectNode<T, S, N> {
    @NonNull
    private final OrderingTerm[] orderingTerms;

    OrderBy(@NonNull SelectNode<T, S, N> parent,
            @NonNull @Size(min = 1) OrderingTerm[] orderingTerms) {
      super(parent);
      this.orderingTerms = orderingTerms;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("ORDER BY ");
      final OrderingTerm[] orderingTerms = this.orderingTerms;
      for (int i = 0, length = orderingTerms.length; i < length; i++) {
        if (i > 0) {
          sb.append(',');
        }
        orderingTerms[i].appendSql(sb);
      }
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("ORDER BY ");
      final OrderingTerm[] orderingTerms = this.orderingTerms;
      for (int i = 0, length = orderingTerms.length; i < length; i++) {
        if (i > 0) {
          sb.append(',');
        }
        orderingTerms[i].appendSql(sb, systemRenamedTables);
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
    public Limit<T, S, N> limit(int nrOfRows) {
      return new Limit<>(this, Integer.toString(nrOfRows));
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class Limit<T, S, N> extends SelectNode<T, S, N> {
    private final String limitClause;

    Limit(@NonNull SelectNode<T, S, N> parent, @NonNull String limitClause) {
      super(parent);
      this.limitClause = limitClause;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("LIMIT ")
          .append(limitClause);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("LIMIT ")
          .append(limitClause);
    }

    /**
     * Add a OFFSET clause to the query.
     * <p>
     * If {@code m} evaluates to a negative value, the results are the same as if it had evaluated to zero.
     *
     * @param m Nr of omitted rows from the result set
     * @return SQL SELECT statement builder
     */
    @CheckResult
    public Offset<T, S, N> offset(int m) {
      return new Offset<>(this, String.valueOf(m));
    }
  }

  /**
   * Builder for SQL SELECT statement.
   *
   * @param <T> Selection return type
   * @param <S> Selection type -- either {@link Select1} or {@link SelectN}
   * @param <N> Selection return type nullability
   */
  public static final class Offset<T, S, N> extends SelectNode<T, S, N> {
    private final String offsetClause;

    Offset(@NonNull SelectNode<T, S, N> parent, @NonNull String offsetClause) {
      super(parent);
      this.offsetClause = offsetClause;
    }

    @Override
    void appendSql(@NonNull StringBuilder sb) {
      sb.append("OFFSET ")
          .append(offsetClause);
    }

    @Override
    void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
      sb.append("OFFSET ")
          .append(offsetClause);
    }
  }

  static void appendColumns(@NonNull StringBuilder sb, @NonNull Column[] columns) {
    for (int i = 0, length = columns.length; i < length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      columns[i].appendSql(sb);
    }
  }

  static void appendColumns(@NonNull StringBuilder sb, @NonNull Column[] columns, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    for (int i = 0, length = columns.length; i < length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      columns[i].appendSql(sb, systemRenamedTables);
    }
  }

	/* ###############################################################################
   * #################################  FUNCTIONS  #################################
	 * ###############################################################################
	 */

  private static final NumericColumn<Long, Long, Number, ?, NotNullable> COUNT = new NumericColumn<>(ANONYMOUS_TABLE, "count(*)", false, LONG_PARSER, false, null);

  /**
   * <p>
   * The avg() function returns the average value of all non-NULL X within a group.
   * String and BLOB values that do not look like numbers are interpreted as 0.
   * </p>
   * The result of avg() is always a floating point value as long as at there is at least
   * one non-NULL input even if all inputs are integers.
   * The result of avg() is NULL if and only if there are no non-NULL inputs.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends NumericColumn<?, ?, ? extends Number, P, N>> NumericColumn<Double, Double, Number, P, N> avg(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "avg(", ")", DOUBLE_PARSER, column.nullable, null);
  }

  /**
   * <p>
   * The avg() function returns the average value of distinct values of column X.
   * String and BLOB values that do not look like numbers are interpreted as 0.
   * </p>
   * <p>
   * Duplicate elements are filtered before being passed into the aggregate function.
   * </p>
   * The result of avg() is always a floating point value as long as at there is at least
   * one non-NULL input even if all inputs are integers.
   * The result of avg() is NULL if and only if there are no non-NULL inputs.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends NumericColumn<?, ?, ? extends Number, P, N>> NumericColumn<Double, Double, Number, P, N> avgDistinct(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "avg(DISTINCT ", ")", DOUBLE_PARSER, column.nullable, null);
  }

  /**
   * Function returns the total number of rows in the group.
   *
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static NumericColumn<Long, Long, Number, ?, NotNullable> count() {
    return COUNT;
  }

  /**
   * The count(X) function returns a count of the number of times that X is not NULL in a group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, X extends Column<?, ?, ?, P, ?>> NumericColumn<Long, Long, Number, P, NotNullable> count(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "count(", ")", LONG_PARSER, false, null);
  }

  /**
   * The count(DISTINCT X) function returns the number of distinct values of column X.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, X extends Column<?, ?, ?, P, ?>> NumericColumn<Long, Long, Number, P, NotNullable> countDistinct(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "count(DISTINCT ", ")", LONG_PARSER, false, null);
  }

  /**
   * The group_concat() function returns a string which is the concatenation of all non-NULL values of X.
   * A comma (",") is used as the separator. The order of the concatenated elements is arbitrary.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ?, P, N>> Column<String, String, CharSequence, P, N> groupConcat(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "group_concat(", ")", STRING_PARSER, column.nullable, null);
  }

  /**
   * The group_concat() function returns a string which is the concatenation of all non-NULL values of X.
   * Parameter "separator" is used as the separator between instances of X.
   * The order of the concatenated elements is arbitrary.
   *
   * @param column    Input of this aggregate function
   * @param separator Separator between instances of X
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ?, P, N>> Column<String, String, CharSequence, P, N> groupConcat(@NonNull X column, @NonNull String separator) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "group_concat(", ",'" + separator + "')", STRING_PARSER, column.nullable, null);
  }

  /**
   * The group_concat() function returns a string which is the concatenation of distinct values of column X.
   * A comma (",") is used as the separator. The order of the concatenated elements is arbitrary.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ?, P, N>> Column<String, String, CharSequence, P, N> groupConcatDistinct(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "group_concat(DISTINCT ", ")", STRING_PARSER, column.nullable, null);
  }

  /**
   * The group_concat() function returns a string which is the concatenation of distinct values of column X.
   * Parameter "separator" is used as the separator between instances of X.
   * The order of the concatenated elements is arbitrary.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ?, P, N>> Column<String, String, CharSequence, P, N> groupConcatDistinct(@NonNull X column, @NonNull String separator) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "group_concat(DISTINCT ", ",'" + separator + "')", STRING_PARSER, column.nullable, null);
  }

  /**
   * The max() aggregate function returns the maximum value of all values in the group.
   * The maximum value is the value that would be returned last in an ORDER BY on the same column.
   * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends Column<T, R, ET, P, N>> Column<T, R, ET, P, N> max(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "max(", ')', column.nullable, null);
  }

  /**
   * The max() aggregate function returns the maximum value of all values in the group.
   * The maximum value is the value that would be returned last in an ORDER BY on the same column.
   * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends NumericColumn<T, R, ET, P, N>> NumericColumn<T, R, ET, P, N> max(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "max(", ')', column.nullable, null);
  }

  /**
   * The max() aggregate function returns the maximum value of distinct values of column X.
   * The maximum value is the value that would be returned last in an ORDER BY on the same column.
   * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends Column<T, R, ET, P, N>> Column<T, R, ET, P, N> maxDistinct(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "max(DISTINCT ", ')', column.nullable, null);
  }

  /**
   * The max() aggregate function returns the maximum value of distinct values of column X.
   * The maximum value is the value that would be returned last in an ORDER BY on the same column.
   * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends NumericColumn<T, R, ET, P, N>> NumericColumn<T, R, ET, P, N> maxDistinct(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "max(DISTINCT ", ')', column.nullable, null);
  }

  /**
   * The min() aggregate function returns the minimum non-NULL value of all values in the group.
   * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
   * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends Column<T, R, ET, P, N>> Column<T, R, ET, P, N> min(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "min(", ')', column.nullable, null);
  }

  /**
   * The min() aggregate function returns the minimum non-NULL value of all values in the group.
   * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
   * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends NumericColumn<T, R, ET, P, N>> NumericColumn<T, R, ET, P, N> min(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "min(", ')', column.nullable, null);
  }

  /**
   * The min() aggregate function returns the minimum value of distinct values of column X.
   * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
   * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends Column<T, R, ET, P, N>> Column<T, R, ET, P, N> minDistinct(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "min(DISTINCT ", ')', column.nullable, null);
  }

  /**
   * The min() aggregate function returns the minimum value of distinct values of column X.
   * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
   * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, ET, N, X extends NumericColumn<T, R, ET, P, N>> NumericColumn<T, R, ET, P, N> minDistinct(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "min(DISTINCT ", ')', column.nullable, null);
  }

  /**
   * <p>
   * Sum function that uses internally total() SQLite aggregate function.
   * </p>
   * The function returns sum of all non-NULL values in the group. If there are no non-NULL input
   * rows then function returns 0.0. The result of function is always a floating point value.
   * This function never throws an integer overflow.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends NumericColumn<?, ?, ? extends Number, P, N>> NumericColumn<Double, Double, Number, P, N> sum(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "total(", ")", DOUBLE_PARSER, column.nullable, null);
  }

  /**
   * <p>
   * Sum function that uses internally total() SQLite aggregate function.
   * </p>
   * The function returns sum of distinct values of column X. If there are no non-NULL input
   * rows then function returns 0.0. The result of function is always a floating point value.
   * This function never throws an integer overflow.
   *
   * @param column Input of this aggregate function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_aggfunc.html">SQLite documentation: Aggregate Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends NumericColumn<?, ?, ? extends Number, P, N>> NumericColumn<Double, Double, Number, P, N> sumDistinct(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "total(DISTINCT ", ")", DOUBLE_PARSER, column.nullable, null);
  }

  /**
   * Join columns.
   * This operator always evaluates to either NULL or a text value.
   *
   * @param columns Columns to concatenate
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_expr.html">SQLite documentation: Expression</a>
   */
  @SafeVarargs
  @NonNull
  @CheckResult
  public static <X extends Column<?, ?, ?, ?, ?>> Column<String, String, CharSequence, ?, com.siimkinks.sqlitemagic.Nullable> concat(@NonNull @Size(min = 2) X... columns) {
    return new FunctionColumn<>(ANONYMOUS_TABLE, columns, "", " || ", "", STRING_PARSER, true, null);
  }

  /**
   * Number value as column.
   *
   * @param val Value
   * @return Column representing provided value
   */
  @NonNull
  @CheckResult
  public static <V extends Number> NumericColumn<V, V, Number, ?, NotNullable> asColumn(@NonNull V val) {
    return new NumericColumn<>(ANONYMOUS_TABLE, numericConstantToSqlString(val), false, parserForNumberType(val), false, null);
  }

  /**
   * CharSequence value as column.
   *
   * @param val Value
   * @return Column representing provided value
   */
  @NonNull
  @CheckResult
  public static <V extends CharSequence> Column<V, V, CharSequence, ?, NotNullable> asColumn(@NonNull V val) {
    return new Column<>(ANONYMOUS_TABLE, "'" + val.toString() + "'", false, STRING_PARSER, false, null);
  }

  /**
   * Value as column.
   *
   * @param val Value
   * @return Column representing provided value
   */
  @NonNull
  @CheckResult
  public static <V> Column<V, V, V, ?, NotNullable> asColumn(@NonNull V val) {
    return SqlUtil.columnForValue(val);
  }

  /**
   * The abs(X) function returns the absolute value of the numeric argument X.
   * Abs(X) returns NULL if X is NULL. If X is the integer -9223372036854775808 then abs(X)
   * throws an integer overflow error since there is no equivalent positive 64-bit two complement value.
   *
   * @param column Input of this function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, T, R, N, ET extends Number, X extends NumericColumn<T, R, ET, P, N>> NumericColumn<T, R, ET, P, N> abs(@NonNull X column) {
    return new FunctionCopyColumn<>(column.table.internalAlias(""), column, "abs(", ')', column.nullable, null);
  }

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
   * @see <a href="http://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ?, P, N>> NumericColumn<Long, Long, Number, P, N> length(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "length(", ")", LONG_PARSER, column.nullable, null);
  }

  // FIXME: 8.03.16 add more informative javadoc

  /**
   * The lower(X) function returns a copy of string X with all ASCII characters converted to lower case.
   * The default built-in lower() function works for ASCII characters only. To do case conversions
   * on non-ASCII characters, load the ICU extension.
   *
   * @param column Input of this function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ? extends CharSequence, P, N>> Column<String, String, CharSequence, P, N> lower(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "lower(", ")", STRING_PARSER, column.nullable, null);
  }

  /**
   * The upper(X) function returns a copy of input string X in which all lower-case ASCII characters
   * are converted to their upper-case equivalent..
   *
   * @param column Input of this function
   * @return Column representing the result of this function
   * @see <a href="http://www.sqlite.org/lang_corefunc.html">SQLite documentation: Core Functions</a>
   */
  @NonNull
  @CheckResult
  public static <P, N, X extends Column<?, ?, ? extends CharSequence, P, N>> Column<String, String, CharSequence, P, N> upper(@NonNull X column) {
    return new FunctionColumn<>(column.table.internalAlias(""), column, "upper(", ")", STRING_PARSER, column.nullable, null);
  }
}
