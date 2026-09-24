package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult

/** Builder for SQL DELETE statement. */
class Delete internal constructor() : DeleteSqlNode(parent = null) {
  override fun appendSql(sb: StringBuilder) {
    sb.append("DELETE")
  }

  companion object {
    /**
     * Create a new builder for SQL DELETE statement.
     *
     * Note that `table` param must be one of annotation processor generated table
     * objects that corresponds to table in a database.
     *
     * Example:
     * ```
     * Delete.from(AUTHOR)
     *   .where(AUTHOR.NAME.is("George"))
     * ```
     *
     * @param table Table to delete from. This param must be one of annotation processor
     * generated table objects that corresponds to table in a database
     * @param T Table object type
     * @return A new builder for SQL DELETE statement
     */
    @CheckResult
    fun <T> from(table: Table<T>) = From(Delete(), table.name)

    /**
     * Create a new builder for SQL DELETE statement.
     *
     * Example:
     * ```
     * Delete.from("author")
     *   .where("author.name=?", "George")
     * ```
     *
     * @param tableName Table to delete from
     * @return A new builder for SQL DELETE statement
     */
    @CheckResult
    fun from(tableName: String): From = From(Delete(), tableName)
  }

  /** Builder for SQL DELETE statement. */
  class From internal constructor(
    parent: Delete,
    internal val tableName: String
  ) : DeleteNode(parent) {
    init {
      deleteBuilder.from = this
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("FROM ")
        .append(tableName)
    }

    /**
     * Define SQL DELETE statement WHERE clause.
     *
     * @param expr WHERE clause expression
     * @return A builder for SQL DELETE statement
     */
    @CheckResult
    fun where(expr: Expr): Where = Where(this, expr)

    /**
     * Define SQL DELETE statement WHERE clause.
     *
     * @param whereClause WHERE clause
     * @param whereArgs WHERE clause arguments
     * @return A builder for SQL DELETE statement
     */
    @CheckResult
    fun where(
      whereClause: String,
      vararg whereArgs: String?
    ): RawWhere = RawWhere(this, whereClause, whereArgs)
  }

  /** Builder for SQL DELETE statement. */
  class Where internal constructor(
    parent: DeleteSqlNode,
    private val expr: Expr
  ) : DeleteNode(parent) {
    init {
      expr.addArgs(deleteBuilder.args)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("WHERE ")
      expr.appendToSql(sb)
    }
  }

  /** Builder for SQL DELETE statement. */
  class RawWhere internal constructor(
    parent: DeleteSqlNode,
    private val clause: String,
    args: Array<out String?>
  ) : DeleteNode(parent) {
    init {
      deleteBuilder.args.addAll(args)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("WHERE ")
        .append(clause)
    }
  }
}
