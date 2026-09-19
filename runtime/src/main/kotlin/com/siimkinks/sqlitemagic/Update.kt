package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode

/** Builder for SQL UPDATE statement. */
class Update internal constructor() : UpdateSqlNode(null) {
  override fun appendSql(sb: StringBuilder) {
    sb.append("UPDATE")
  }

  companion object {
    /**
     * Create a new builder for SQL UPDATE statement with conflict algorithm.
     *
     * @param conflictAlgorithm Conflict resolution algorithm to use
     * @return A new builder for SQL UPDATE statement
     */
    @JvmStatic
    @CheckResult
    fun withConflictAlgorithm(
      @ConflictAlgorithm conflictAlgorithm: Int
    ) = UpdateConflictAlgorithm(
      parent = Update(),
      conflictAlgorithm = conflictAlgorithm
    )

    /**
     * Create a new builder for SQL UPDATE statement.
     *
     * Note that `table` param must be one of annotation processor generated table
     * objects that corresponds to table in a database.
     *
     * Example:
     * ```
     * import static com.example.model.AuthorTable.AUTHOR;
     *
     * // [...]
     *
     * Update.table(AUTHOR)...
     * ```
     *
     * @param table Table to update. This param must be one of annotation processor
     * generated table objects that corresponds to table in a database
     * @param T Table object type
     * @return A new builder for SQL UPDATE statement
     */
    @JvmStatic
    @CheckResult
    fun <T> table(table: Table<T>) = TableNode<T>(
      parent = Update(),
      tableName = table.name
    )

    /**
     * Create a new builder for SQL UPDATE statement.
     *
     * Example:
     * ```
     * Update.table("author")...
     * ```
     *
     * @param tableName Table to update
     * @return A new builder for SQL UPDATE statement
     */
    @JvmStatic
    @CheckResult
    fun table(tableName: String): TableNode<*> = TableNode<Any>(
      parent = Update(),
      tableName = tableName
    )
  }

  /** Builder for SQL UPDATE statement. */
  class UpdateConflictAlgorithm internal constructor(
    parent: Update,
    @ConflictAlgorithm
    private val conflictAlgorithm: Int
  ) : UpdateSqlNode(parent) {
    override fun appendSql(sb: StringBuilder) {
      sb.append(ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm])
    }

    /**
     * Define a table to update.
     *
     * Note that `table` param must be one of annotation processor generated table
     * objects that corresponds to table in a database.
     *
     * Example:
     * ```
     * import static com.example.model.AuthorTable.AUTHOR;
     *
     * // [...]
     *
     * Update.table(AUTHOR)...
     * ```
     *
     * @param table Table to update. This param must be one of annotation processor
     * generated table objects that corresponds to table in a database
     * @param T Table object type
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <T> table(table: Table<T>) = TableNode<T>(
      parent = this,
      tableName = table.name
    )

    /**
     * Define a table to update.
     *
     * Example:
     * ```
     * Update.table("author")...
     * ```
     *
     * @param tableName Table to update
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun table(tableName: String): TableNode<*> = TableNode<Any>(
      parent = this,
      tableName = tableName
    )
  }

  /**
   * Builder for SQL UPDATE statement.
   *
   * @param T Updated table object type
   */
  class TableNode<T> internal constructor(
    parent: UpdateSqlNode,
    @JvmField
    internal val tableName: String
  ) : UpdateSqlNode(parent) {
    init {
      updateBuilder.tableNode = this
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append(tableName)
    }

    /**
     * Update a not nullable column with new not nullable value.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param value A new value to set for updated column
     * @param V Value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET> set(
      column: Column<V, R, ET, T, NotNullable>,
      value: V & Any
    ): Set<T> = Set(
      parent = this,
      firstUpdate = UpdateColumn(column) IS value
    )

    /**
     * Update a nullable column with new nullable value.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param value A new value to set for updated column
     * @param V Value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET> setNullable(
      column: Column<V, R, ET, T, Nullable>,
      value: V?
    ): Set<T> = Set(
      parent = this,
      firstUpdate = UpdateColumn(column).isNullable(value)
    )

    /**
     * Update a column with the value of another column.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param assignmentColumn Column who's value to assign to `column`.
     * This param must be one of annotation processor generated column objects that corresponds to column in a database
     * table
     * @param V Column value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @param N Column nullability
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET, N> set(
      column: Column<V, R, ET, T, N>,
      assignmentColumn: Column<*, *, out ET, *, in N>
    ): Set<T> = Set(
      parent = this,
      firstUpdate = UpdateColumn(column) IS assignmentColumn
    )

    /**
     * Update a column with the value of inner SELECT statement result.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param select Inner select statement who's result will be assigned to `column`.
     * Select statement must return 1x1 result set.
     * @param V Column value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @param N Column nullability
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET, N> set(
      column: Column<V, R, ET, T, N>,
      select: SelectNode<out ET, Select1, in N>
    ): Set<T> = Set(
      parent = this,
      firstUpdate = UpdateColumn(column) IS select
    )

    /**
     * Update a column with new value.
     *
     * @param columnName Column to update
     * @param value A new value to set for updated column
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun set(
      columnName: String,
      value: String?
    ): Set<T> = Set(
      parent = this,
      columnName = columnName,
      value = value
    )
  }

  private class UpdateColumn<T, R, ET, P, N>(
    private val parentColumn: Column<T, R, ET, P, N>
  ) : Column<T, R, ET, P, N>(
    parentColumn.table,
    parentColumn.name,
    parentColumn.allFromTable,
    parentColumn.valueParser,
    parentColumn.nullable,
    parentColumn.alias
  ) {
    fun isNullable(value: T?): Expr = Expr1(this, "=?", value?.let(::toSqlArg))

    override fun toSqlArg(value: T & Any): String = parentColumn.toSqlArg(value)

    override fun appendSql(sb: StringBuilder) {
      sb.append(name)
    }
  }

  /**
   * Builder for SQL UPDATE statement.
   *
   * @param T Updated table object type
   */
  class Set<T> internal constructor(parent: UpdateSqlNode) : UpdateNode(parent) {
    private sealed interface Assignment {
      fun appendSql(sb: StringBuilder)

      class Expression(private val expr: Expr) : Assignment {
        override fun appendSql(sb: StringBuilder) = expr.appendToSql(sb)
      }

      class Raw(private val columnName: String) : Assignment {
        override fun appendSql(sb: StringBuilder) {
          sb.append(columnName)
            .append("=?")
        }
      }
    }

    private val assignments = ArrayList<Assignment>(1)

    internal constructor(
      parent: UpdateSqlNode,
      firstUpdate: Expr
    ) : this(parent) {
      add(firstUpdate)
    }

    internal constructor(
      parent: UpdateSqlNode,
      columnName: String,
      value: String?
    ) : this(parent) {
      addRaw(columnName, value)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("SET ")
      assignments.forEachIndexed { index, assignment ->
        if (index > 0) {
          sb.append(',')
        }
        assignment.appendSql(sb)
      }
    }

    private fun add(expr: Expr): Set<T> = apply {
      assignments += Assignment.Expression(expr)
      expr.addArgs(updateBuilder.args)
    }

    private fun addRaw(
      columnName: String,
      value: String?
    ): Set<T> = apply {
      assignments += Assignment.Raw(columnName)
      updateBuilder.args += value
    }

    /**
     * Update a not nullable column with new not nullable value.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param value A new value to set for updated column
     * @param V Value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET> set(
      column: Column<V, R, ET, T, NotNullable>,
      value: V & Any
    ): Set<T> = add(UpdateColumn(column) IS value)

    /**
     * Update a nullable column with new nullable value.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param value A new value to set for updated column
     * @param V Value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET> setNullable(
      column: Column<V, R, ET, T, Nullable>,
      value: V?
    ): Set<T> = add(UpdateColumn(column).isNullable(value))

    /**
     * Update a column with the value of another column.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param assignmentColumn Column who's value to assign to `column`.
     * This param must be one of annotation processor generated column objects that corresponds to column in a database
     * table
     * @param V Column value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @param N Column nullability
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET, N> set(
      column: Column<V, R, ET, T, N>,
      assignmentColumn: Column<*, *, out ET, *, in N>
    ): Set<T> = add(UpdateColumn(column) IS assignmentColumn)

    /**
     * Update a column with the value of inner SELECT statement result.
     *
     * @param column Column to update. This param must be one of annotation processor
     * generated column objects that corresponds to column in a database table
     * @param select Inner select statement who's result will be assigned to `column`.
     * Select statement must return 1x1 result set.
     * @param V Column value type
     * @param R Column return type
     * @param ET Column equivalent type
     * @param N Column nullability
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun <V, R, ET, N> set(
      column: Column<V, R, ET, T, N>,
      select: SelectNode<out ET, Select1, in N>
    ): Set<T> = add(UpdateColumn(column) IS select)

    /**
     * Update a column with new value.
     *
     * @param columnName Column to update
     * @param value A new value to set for updated column
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun set(
      columnName: String,
      value: String?
    ): Set<T> = addRaw(columnName, value)

    /**
     * Define SQL UPDATE statement WHERE clause.
     *
     * @param expr WHERE clause expression
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun where(expr: Expr) = Where(
      parent = this,
      expr = expr
    )

    /**
     * Define SQL UPDATE statement WHERE clause.
     *
     * @param whereClause WHERE clause
     * @param whereArgs WHERE clause arguments
     * @return SQL UPDATE statement builder
     */
    @CheckResult
    fun where(
      whereClause: String,
      vararg whereArgs: String?
    ) = RawWhere(
      parent = this,
      clause = whereClause,
      args = whereArgs
    )
  }

  /** Builder for SQL UPDATE statement. */
  class Where internal constructor(
    parent: UpdateSqlNode,
    private val expr: Expr
  ) : UpdateNode(parent) {
    init {
      expr.addArgs(updateBuilder.args)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("WHERE ")
      expr.appendToSql(sb)
    }
  }

  /** Builder for SQL UPDATE statement. */
  class RawWhere internal constructor(
    parent: UpdateSqlNode,
    private val clause: String,
    args: Array<out String?>
  ) : UpdateNode(parent) {
    init {
      updateBuilder.args.addAll(args)
    }

    override fun appendSql(sb: StringBuilder) {
      sb.append("WHERE ")
        .append(clause)
    }
  }
}
