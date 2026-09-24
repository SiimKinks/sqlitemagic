package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.Size
import com.siimkinks.sqlitemagic.Utils.TABLE_ALL_PARSER
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

/** Row mapper for the current selection shape. */
fun interface TableMapper<T> {
  operator fun invoke(
    columnPositions: SimpleArrayMap<String, Int>?,
    tableGraphNodeNames: SimpleArrayMap<String, String>?,
    queryDeep: Boolean
  ): Query.Mapper<T>
}

/** Contributes one generated table's relationships to a query graph. */
fun interface TableQueryGraphContributor {
  fun QueryGraphScope.contribute(
    table: Table<*>,
    tableAlias: Table<*>,
    nodeName: String
  )
}

/**
 * Metadata of a table in a database.
 *
 * @param T Table object type
 */
open class Table<T> protected constructor(
  internal val name: String,
  internal val alias: String?,
  internal val nrOfColumns: Int,
  internal val mapper: TableMapper<T> = { _, _, _ ->
    error("Table does not provide row mapping")
  },
  internal val addDeepQueryParts: TableQueryGraphContributor = { _, _, _ -> },
  internal val addShallowQueryParts: TableQueryGraphContributor = { _, _, _ -> }
) {
  internal val hasAlias = alias != null
  internal val nameInQuery = alias ?: name
  private val selectAllColumn = Column<Any, Any, Any, T, NotNullable>(
    table = this,
    name = "*",
    allFromTable = true,
    valueParser = TABLE_ALL_PARSER,
    nullable = false,
    alias = null
  )

  internal open fun appendToSqlFromClause(sb: StringBuilder) {
    sb.append(name)
    if (hasAlias) {
      sb.append(" AS ")
        .append(alias)
    }
  }

  /**
   * Gives a runtime table the opportunity to perfect a selection at build time.
   *
   * @param observedTables Tables that are being selected
   * @param tableGraphNodeNames Selection graph node names
   * @param columnPositions Column positions in the selection
   * @return Whether selection should be deep.
   */
  internal open fun perfectSelection(
    observedTables: ArrayList<String>,
    tableGraphNodeNames: SimpleArrayMap<String, String>?,
    columnPositions: SimpleArrayMap<String, Int>?
  ): Boolean {
    if (name !in observedTables) {
      observedTables.add(name)
    }
    return false
  }

  internal fun internalAlias(alias: String) = Table<T>(name, alias, nrOfColumns)

  /**
   * Create an alias for this table.
   *
   * @param alias The alias name
   * @return New table with provided alias.
   */
  @CheckResult
  open fun `as`(alias: String) = Table<T>(name, alias, nrOfColumns)

  /**
   * All columns from this table (`*`).
   *
   * @return Column that represents all columns in this table
   */
  fun all() = selectAllColumn

  /**
   * Create a join `ON` clause.
   *
   * @param expr Expression to use in join `ON` clause
   * @return Join clause
   */
  @CheckResult
  fun on(expr: Expr) = object : JoinClause(
    table = this@Table,
    operator = "",
    constraint = "ON "
  ) {
    override fun appendSql(sb: StringBuilder) {
      super.appendSql(sb)
      expr.appendToSql(sb)
    }

    override fun appendSql(
      sb: StringBuilder,
      systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
    ) {
      super.appendSql(sb, systemRenamedTables)
      expr.appendToSql(sb, systemRenamedTables)
    }

    override fun containsColumn(column: Column<*, *, *, *, *>) = expr.containsColumn(column)

    override fun addArgs(args: ArrayList<String?>) {
      super.addArgs(args)
      expr.addArgs(args)
    }
  }

  /**
   * Create a join `USING` clause.
   *
   * Each of the columns specified must exist in the datasets to both the left and right
   * of the join-operator. For each pair of columns, the expression `lhs.X = rhs.X`
   * is evaluated for each row of the cartesian product as a boolean expression.
   * Only rows for which all such expressions evaluates to true are included from the
   * result set. When comparing values as a result of a USING clause, the normal rules
   * for handling affinities, collation sequences and NULL values in comparisons apply.
   * The column from the dataset on the left-hand side of the join-operator is considered
   * to be on the left-hand side of the comparison operator (=) for the purposes of
   * collation sequence and affinity precedence.
   *
   * For each pair of columns identified by a USING clause, the column from the
   * right-hand dataset is omitted from the joined dataset. This is the only difference
   * between a USING clause and its equivalent ON constraint.
   *
   * @param columns Columns to use in the USING clause
   * @return Join clause
   */
  @CheckResult
  fun using(
    @Size(min = 1) vararg columns: Column<*, *, *, *, *>
  ) = object : JoinClause(
    table = this@Table,
    operator = "",
    constraint = buildString(8 + columns.size * 12) {
      append("USING (")
      columns.forEachIndexed { index, column ->
        if (index > 0) {
          append(',')
        }
        append(column.name)
      }
      append(')')
    }
  ) {
    override fun containsColumn(column: Column<*, *, *, *, *>) = column in columns
  }

  internal fun baseNameEquals(other: Any?): Boolean = this === other ||
      other is Table<*> && name == other.name

  override fun equals(other: Any?): Boolean = this === other ||
      other is Table<*> && nameInQuery == other.nameInQuery

  override fun hashCode() = nameInQuery.hashCode()

  override fun toString() = name

  companion object {
    val ANONYMOUS_TABLE: Table<*> = Table<Any>(
      name = "",
      alias = null,
      nrOfColumns = 1
    )
  }
}
