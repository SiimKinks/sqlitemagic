package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDoneException
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.Select.OrderingTerm
import com.siimkinks.sqlitemagic.Select.OrderingTerm.Companion.ASC
import com.siimkinks.sqlitemagic.Select.OrderingTerm.Companion.DESC
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.SqlUtil.quoteSqlStringLiteral
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.Utils.STRING_PARSER
import com.siimkinks.sqlitemagic.Utils.ValueParser
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import java.util.LinkedList

/**
 * A column used in queries and conditions.
 *
 * @param T Exact type
 * @param R Return type (when this column is queried)
 * @param ET Equivalent type
 * @param P Parent table type
 * @param N Column nullability
 */
open class Column<T, R, ET, P, N>(
  internal val table: Table<P>,
  internal val name: String,
  internal val allFromTable: Boolean = false,
  internal val valueParser: ValueParser<*>,
  internal val nullable: Boolean,
  internal val alias: String?,
  internal val nameInQuery: String = when {
    table.nameInQuery.isEmpty() -> name
    else -> "${table.nameInQuery}.$name"
  },
  internal val valueAdapter: ColumnValueAdapter<T>? = null
) {
  internal open fun appendSql(sb: StringBuilder) {
    sb.append(nameInQuery)
  }

  internal open fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    val aliases = systemRenamedTables[table.name]
    if (table.hasAlias || aliases == null) {
      sb.append(nameInQuery)
      return
    }
    if (aliases.size > 1) {
      throw SQLException("Ambiguous column $nameInQuery; aliases=$aliases")
    }
    sb.append(aliases.first())
      .append('.')
      .append(name)
  }

  internal fun appendAliasDeclarationIfNeeded(sb: StringBuilder) {
    if (hasAlias()) {
      sb.append(" AS '")
        .append(getAppendableAlias())
        .append('\'')
    }
  }

  internal fun hasAlias() = !alias.isNullOrEmpty() && !allFromTable

  internal fun getAppendableAlias() = alias
    ?.substringAfterLast('.')
    ?: throw NullPointerException("Column alias == null")

  internal open fun addSelectedTables(result: StringArraySet) {
    result.add(table.name)
  }

  internal open fun addArgs(args: ArrayList<String?>) = Unit

  /**
   * This method gives columns the ability to add any extra tables that need to be observed by the main query.
   *
   * @param tables Already observed tables
   */
  internal open fun addObservedTables(tables: ArrayList<String>) = Unit

  /**
   * Compile columns.
   *
   * This method has the following responsibilities:
   *
   * - Must append itself to compiledCols -- formatted as appearing in SQL result column
   * - Must add its position to columnPositions offsetting from provided columnOffset
   * - Must return columnOffset + selected columns count
   */
  @CheckResult
  internal open fun compile(
    columnPositions: SimpleArrayMap<String, Int>,
    compiledCols: StringBuilder,
    columnOffset: Int
  ): Int = compileWithoutSystemRenames(
    columnPositions = columnPositions,
    compiledCols = compiledCols,
    columnOffset = columnOffset
  )

  private fun compileWithoutSystemRenames(
    columnPositions: SimpleArrayMap<String, Int>,
    compiledCols: StringBuilder,
    columnOffset: Int
  ): Int {
    compiledCols.append(nameInQuery)
    appendAliasDeclarationIfNeeded(compiledCols)
    putColumnPosition(
      columnPositions = columnPositions,
      columnId = when {
        allFromTable -> table.nameInQuery
        else -> nameInQuery
      },
      pos = columnOffset,
      column = this
    )
    val selectedColumnCount = when {
      allFromTable -> table.nrOfColumns
      else -> 1
    }
    return columnOffset + selectedColumnCount
  }

  /**
   * Compile columns.
   *
   * This method has the following responsibilities:
   *
   * - Must append itself to compiledCols -- formatted as appearing in SQL result column
   * - Must add its position to columnPositions offsetting from provided columnOffset
   * - Must return columnOffset + selected columns count
   */
  @CheckResult
  internal open fun compile(
    columnPositions: SimpleArrayMap<String, Int>,
    compiledCols: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>,
    columnOffset: Int
  ): Int {
    val aliases = systemRenamedTables[table.name]
    if (table.hasAlias || aliases == null) {
      return compileWithoutSystemRenames(
        columnPositions = columnPositions,
        compiledCols = compiledCols,
        columnOffset = columnOffset
      )
    }

    var offset = columnOffset
    when {
      allFromTable -> aliases.forEachIndexed { index, alias ->
        if (index > 0) {
          compiledCols.append(',')
        }
        compiledCols
          .append(alias)
          .append('.')
          .append(name)
        putColumnPosition(
          columnPositions = columnPositions,
          columnId = alias,
          pos = offset,
          column = this
        )
        offset += table.nrOfColumns
      }
      else -> aliases.forEachIndexed { index, alias ->
        if (index > 0) {
          compiledCols.append(',')
        }
        val newColumn = "$alias.$name"
        compiledCols.append(newColumn)
        putColumnPosition(
          columnPositions = columnPositions,
          columnId = newColumn,
          pos = offset,
          column = this
        )
        offset++
      }
    }
    return offset
  }

  internal open fun toSqlArg(value: T & Any) = valueAdapter?.toSqlArg(value) ?: value.toString()

  @Suppress("UNCHECKED_CAST")
  internal open fun <V> getFromCursor(cursor: Cursor): V? = when {
    valueAdapter != null -> valueAdapter.getFromCursor(
      cursor = cursor,
      nullable = nullable
    )
    nullable && cursor.isNull(0) -> null
    else -> valueParser.parseFromCursor(cursor) as V?
  }

  @Suppress("UNCHECKED_CAST")
  internal open fun <V> getFromStatement(statement: SupportSQLiteStatement): V? = when {
    valueAdapter != null -> valueAdapter.getFromStatement(statement)
    else -> try {
      valueParser.parseFromStatement(statement) as V?
    } catch (_: SQLiteDoneException) {
      // query returned no results
      null
    }
  }

  /**
   * Create an alias for this column.
   *
   * Note that column aliases are quoted and thus case-sensitive!
   *
   * @param alias The alias name
   * @return New column with provided alias.
   */
  @CheckResult
  open fun `as`(alias: String): Column<T, R, ET, P, N> = Column(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    valueAdapter = valueAdapter
  )

  /**
   * Mark this column as a part of the provided table.
   *
   * @param table Table this column should belong to
   * @return New column in the provided table.
   */
  @CheckResult
  open fun <NewTableType> inTable(table: Table<NewTableType>) = Column<T, R, ET, NewTableType, N>(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    valueAdapter = valueAdapter
  )

  /**
   * Convert this column to not-nullable column.
   *
   * @return Non-nullable column.
   */
  @CheckResult
  @Suppress("UNCHECKED_CAST")
  fun toNotNullable(): Column<T, R, ET, P, NotNullable> {
    if (nullable && SqliteMagic.LOGGING_ENABLED) {
      val errorMsg = "Converting nullable column [$this] to not nullable. This might cause data inconsistencies!"
      LogUtil.logError(errorMsg, IllegalStateException(errorMsg))
    }
    return this as Column<T, R, ET, P, NotNullable>
  }

  /**
   * Create ORDER BY ordering term.
   *
   * Orders rows in ascending order.
   *
   * @return Ordering term to be used in `orderBy` method.
   */
  @CheckResult
  fun asc() = OrderingTerm(this, null, ASC)

  /**
   * Create ORDER BY ordering term.
   *
   * Orders rows in descending order.
   *
   * @return Ordering term to be used in `orderBy` method.
   */
  @CheckResult
  fun desc() = OrderingTerm(this, null, DESC)

  /**
   * Concat column with this column.
   * This operator always evaluates to either NULL or a text value.
   *
   * @param column Column to concatenate
   * @param X Column type that extends [Column]
   * @return Column representing the result of this function
   * @see [SQLite documentation: Expression](http://www.sqlite.org/lang_expr.html)
   */
  @CheckResult
  fun <X : Column<*, *, *, *, *>> concat(
    column: X
  ): Column<String, String, CharSequence, *, Nullable> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumns = arrayOf(this, column),
    prefix = "",
    separator = " || ",
    suffix = "",
    valueParser = STRING_PARSER,
    nullable = nullable || column.nullable,
    alias = null
  )

  /**
   * The replace(X,Y,Z) function returns a string formed by substituting string
   * Z for every occurrence of string Y in string X.
   *
   * The BINARY collating sequence is used for comparisons. If Y is an empty string
   * then return X unchanged. If Z is not initially a string, it is cast to a
   * UTF-8 string prior to processing.
   *
   * @param target The sequence of char values to be replaced
   * @param replacement The replacement sequence of char values
   * @return Column representing the result of this function
   * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
   */
  @CheckResult
  fun replace(
    target: CharSequence,
    replacement: CharSequence
  ): Column<String, String, CharSequence, *, N> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumn = this,
    prefix = "replace(",
    suffix = ",${quoteSqlStringLiteral(target)},${quoteSqlStringLiteral(replacement)})",
    valueParser = STRING_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * The substr(X,Y) function returns a substring of input string X that begins with
   * the Y-th character.
   *
   * This function returns all characters through the end of
   * the string X beginning with the Y-th. The left-most character of X is number 1.
   * If Y is negative then the first character of the substring is found by counting
   * from the right rather than the left.
   * If X is a string then characters indices refer to actual UTF-8 characters.
   * If X is a BLOB then the indices refer to bytes.
   *
   * Examples:
   * ```
   * "zero".substring(0) returns "zero"
   * "first".substring(1) returns "first"
   * "unhappy".substring(3) returns "happy"
   * "Harbison".substring(4) returns "bison"
   * "emptiness".substring(10) returns "" (an empty string)
   * "last".substring(-1) returns "t"
   * "substring".substring(-6) returns "string"
   * ```
   *
   * @param beginPos The begin position (`Y`). The first position in the string is always 1
   * @return Column representing the result of this function
   * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
   */
  @CheckResult
  fun substring(beginPos: Int): Column<String, String, CharSequence, *, N> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumn = this,
    prefix = "substr(",
    suffix = ",$beginPos)",
    valueParser = STRING_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * The substring(X,Y,Z) function returns a substring of input string X that begins with
   * the Y-th character and which is Z characters long.
   *
   * The left-most character of X is number 1. If Y is negative then the first character
   * of the substring is found by counting from the right rather than the left.
   * If Z is negative then the abs(Z) characters preceding the Y-th character are returned.
   * If X is a string then characters indices refer to actual UTF-8 characters.
   * If X is a BLOB then the indices refer to bytes.
   *
   * Examples:
   * ```
   * "first".substring(1, 2) returns "fi"
   * "smiles".substring(2, 5) returns "mile"
   * "hamburger".substring(4, -3) returns "ham"
   * "last".substring(-1, -2) returns "as"
   * ```
   *
   * @param beginPos The begin position (`Y`). The first position in the string is always 1
   * @param charsToReturn Number of characters to be returned (`Z`)
   * @return Column representing the result of this function
   * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
   */
  @CheckResult
  fun substring(
    beginPos: Int,
    charsToReturn: Int
  ): Column<String, String, CharSequence, *, N> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumn = this,
    prefix = "substr(",
    suffix = ",$beginPos,$charsToReturn)",
    valueParser = STRING_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * The trim(X) function removes spaces from both ends of X.
   *
   * @return Column representing the result of this function
   * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
   */
  @CheckResult
  fun trim(): Column<String, String, CharSequence, *, N> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumn = this,
    prefix = "trim(",
    suffix = ")",
    valueParser = STRING_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * The trim(X,Y) function returns a string formed by removing any and all characters
   * that appear in Y from both ends of X.
   *
   * @param trimString The string that will be removed from both sides of the target column
   * @return Column representing the result of this function
   * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
   */
  @CheckResult
  fun trim(trimString: CharSequence): Column<String, String, CharSequence, *, N> = FunctionColumn(
    table = ANONYMOUS_TABLE,
    wrappedColumn = this,
    prefix = "trim(",
    suffix = ",${quoteSqlStringLiteral(trimString)})",
    valueParser = STRING_PARSER,
    nullable = nullable,
    alias = null
  )

  /**
   * This column as expression.
   *
   * @return Expression
   */
  @CheckResult
  fun toExpr() = Expr(this, "")

  /**
   * This column = value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @CheckResult
  fun `is`(value: T & Any): Expr = Expr1(this, "=?", toSqlArg(value))

  /**
   * This column = column.
   *
   * @param column The column to test against this column
   * @param C Column type that extends [Column] and has equivalent type to this column
   * @return Expression
   */
  @CheckResult
  fun <C : Column<*, *, out ET, *, *>> `is`(column: C): Expr = ExprC(this, "=", column)

  /**
   * This column = (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun `is`(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, "=", select)

  /**
   * This column != value.
   *
   * @param value The value to test against this column
   * @return Expression
   */
  @CheckResult
  fun isNot(value: T & Any): Expr = Expr1(this, "!=?", toSqlArg(value))

  /**
   * This column != column.
   *
   * @param column The column to test against this column
   * @param C Column type that extends [Column] and has equivalent type to this column
   * @return Expression
   */
  @CheckResult
  fun <C : Column<*, *, out ET, *, *>> isNot(column: C): Expr = ExprC(this, "!=", column)

  /**
   * This column != (SELECT... ).
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun isNot(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, "!=", select)

  /**
   * This column IS NULL.
   *
   * @return Expression
   */
  @CheckResult
  fun isNull() = Expr(this, " IS NULL")

  /**
   * This column IS NOT NULL.
   *
   * @return Expression
   */
  @CheckResult
  fun isNotNull() = Expr(this, " IS NOT NULL")

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
  @CheckResult
  fun like(likeRegex: String): Expr = Expr1(this, " LIKE ?", likeRegex)

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
  @CheckResult
  fun notLike(likeRegex: String): Expr = Expr1(this, " NOT LIKE ?", likeRegex)

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
  @CheckResult
  fun glob(globRegex: String): Expr = Expr1(this, " GLOB ?", globRegex)

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
  @CheckResult
  fun notGlob(globRegex: String): Expr = Expr1(this, " NOT GLOB ?", globRegex)

  /**
   * Create an expression to check this column against several values.
   *
   * SQL: this IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @CheckResult
  fun `in`(values: Collection<T & Any>): Expr = createMembershipExpression(
    values = values,
    valueCount = values.size,
    prefix = " IN (",
    emptyResult = "0"
  )

  /**
   * Create an expression to check this column against several values.
   *
   * SQL: this IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @CheckResult
  fun `in`(vararg values: T & Any): Expr = createMembershipExpression(
    values = values.asIterable(),
    valueCount = values.size,
    prefix = " IN (",
    emptyResult = "0"
  )

  /**
   * Create an expression to check this column against a subquery.
   * Note that the subquery must return exactly one column.
   *
   * SQL: this IN (SELECT...)
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun `in`(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, " IN ", select)

  /**
   * Create an expression to check this column against several values.
   *
   * SQL: this NOT IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @CheckResult
  fun notIn(values: Collection<T & Any>): Expr = createMembershipExpression(
    values = values,
    valueCount = values.size,
    prefix = " NOT IN (",
    emptyResult = "1"
  )

  /**
   * Create an expression to check this column against several values.
   *
   * SQL: this NOT IN (values...)
   *
   * @param values The values to test against this column
   * @return Expression
   */
  @CheckResult
  fun notIn(vararg values: T & Any): Expr = createMembershipExpression(
    values = values.asIterable(),
    valueCount = values.size,
    prefix = " NOT IN (",
    emptyResult = "1"
  )

  /**
   * Create an expression to check this column against a subquery.
   * Note that the subquery must return exactly one column.
   *
   * SQL: this NOT IN (SELECT...)
   *
   * @param select The subquery to test against this column
   * @return Expression
   */
  @CheckResult
  fun notIn(select: SelectNode<out ET?, Select1, *>): Expr = ExprS(this, " NOT IN ", select)

  private fun createMembershipExpression(
    values: Iterable<T & Any>,
    valueCount: Int,
    prefix: String,
    emptyResult: String
  ): Expr {
    if (valueCount == 0) {
      return Expr.raw("?", emptyResult)
    }
    val iterator = values.iterator()
    val args = Array(valueCount) { toSqlArg(iterator.next()) }
    val sql = buildString(prefix.length + 1 + (valueCount shl 1)) {
      append(prefix)
      repeat(valueCount) { index ->
        if (index > 0) {
          append(',')
        }
        append('?')
      }
      append(')')
    }
    return ExprN(this, sql, args)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Column<*, *, *, *, *>) return false
    return table.baseNameEquals(other.table) && name == other.name
  }

  override fun hashCode() = name.hashCode()

  override fun toString() = "$table.$name"

  companion object {
    /*
     * Makes copy that does not consider systemRenamedTables when building SQL.
     * Should only be used in SQL compiling. Copies are made with table structure column
     * constants -- we don't have to consider any internal column type here.
     * Typically used in making renamed joins.
     */
    internal fun <T, R, ET, P, N> internalCopy(
      newTable: Table<P>,
      column: Column<T, R, ET, *, N>
    ): Column<T, R, ET, P, N> = object : Column<T, R, ET, P, N>(
      table = newTable,
      name = column.name,
      allFromTable = column.allFromTable,
      valueParser = column.valueParser,
      nullable = column.nullable,
      alias = column.alias
    ) {
      override fun toSqlArg(value: T & Any) = column.toSqlArg(value)

      override fun <V> getFromCursor(cursor: Cursor): V? =
        column.getFromCursor(cursor)

      override fun <V> getFromStatement(statement: SupportSQLiteStatement): V? =
        column.getFromStatement(statement)

      override fun appendSql(
        sb: StringBuilder,
        systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
      ) = super.appendSql(sb)
    }

    internal fun <T, R, ET, P, N> putColumnPosition(
      columnPositions: SimpleArrayMap<String, Int>,
      columnId: String?,
      pos: Int,
      column: Column<T, R, ET, P, N>
    ) {
      columnId?.let { columnPositions.put(it, pos) }
      column.alias?.let { columnPositions.put(it, pos) }
    }
  }
}
