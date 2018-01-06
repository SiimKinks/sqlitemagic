package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;

/**
 * Builder for SQL DELETE statement.
 */
public final class Delete extends DeleteSqlNode {
  Delete() {
    super(null);
  }

  @Override
  protected void appendSql(@NonNull StringBuilder sb) {
    sb.append("DELETE");
  }

  /**
   * Create a new builder for SQL DELETE statement.
   * <p>
   * Note that {@code table} param must be one of annotation processor generated table
   * objects that corresponds to table in a database.<br>
   * Example:
   * <pre>{@code
   * import static com.example.model.AuthorTable.AUTHOR;
   *
   * // [...]
   *
   * Delete.from(AUTHOR)
   *       .where(AUTHOR.NAME.is("George"));
   * }</pre>
   *
   * @param table Table to delete from. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @param <T>   Table object type
   * @return A new builder for SQL DELETE statement
   */
  @CheckResult
  public static <T> From from(@NonNull Table<T> table) {
    return new From(new Delete(), table.name);
  }

  /**
   * Create a new builder for SQL DELETE statement.
   * <p>
   * Example:
   * <pre>{@code
   * Delete.from("author")
   *       .where("author.name=?", "George");
   * }</pre>
   *
   * @param tableName Table to delete from
   * @return A new builder for SQL DELETE statement
   */
  @CheckResult
  public static From from(@NonNull String tableName) {
    return new From(new Delete(), tableName);
  }

  /**
   * Builder for SQL DELETE statement.
   */
  public static final class From extends DeleteNode {
    @NonNull
    final String tableName;

    From(@NonNull Delete parent, @NonNull String tableName) {
      super(parent);
      this.tableName = tableName;
      deleteBuilder.from = this;
    }

    @Override
    protected void appendSql(@NonNull StringBuilder sb) {
      sb.append("FROM ");
      sb.append(tableName);
    }

    /**
     * Define SQL DELETE statement WHERE clause.
     *
     * @param expr WHERE clause expression
     * @return A builder for SQL DELETE statement
     */
    @CheckResult
    public Where where(@NonNull Expr expr) {
      return new Where(this, expr);
    }

    /**
     * Define SQL DELETE statement WHERE clause.
     *
     * @param whereClause WHERE clause
     * @param whereArgs   WHERE clause arguments
     * @return A builder for SQL DELETE statement
     */
    @CheckResult
    public RawWhere where(@NonNull String whereClause, @Nullable String... whereArgs) {
      return new RawWhere(this, whereClause, whereArgs);
    }
  }

  /**
   * Builder for SQL DELETE statement.
   */
  public static final class Where extends DeleteNode {
    @NonNull
    private final Expr expr;

    Where(@NonNull DeleteSqlNode parent, @NonNull Expr expr) {
      super(parent);
      this.expr = expr;
      expr.addArgs(deleteBuilder.args);
    }

    @Override
    protected void appendSql(@NonNull StringBuilder sb) {
      sb.append("WHERE ");
      expr.appendToSql(sb);
    }
  }

  /**
   * Builder for SQL DELETE statement.
   */
  public static final class RawWhere extends DeleteNode {
    @NonNull
    private final String clause;

    RawWhere(@NonNull DeleteSqlNode parent, @NonNull String clause, @Nullable String[] args) {
      super(parent);
      this.clause = clause;
      if (args != null && args.length > 0) {
        Collections.addAll(deleteBuilder.args, args);
      }
    }

    @Override
    protected void appendSql(@NonNull StringBuilder sb) {
      sb.append("WHERE ");
      sb.append(clause);
    }
  }
}
