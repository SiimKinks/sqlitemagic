@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.SelectNode
import com.siimkinks.sqlitemagic.internal.sql.nativeValue

/**
 * Builder for SQL SELECT statement.
 *
 * Start queries with [SELECT].
 */
object Select {
  /**
   * Create raw SQL SELECT statement.
   *
   * Read the results with `readAs` or get a cursor with `toCursor`. Define the tables that the query reads with
   * `observes`, otherwise `observe` runs the query only once.
   *
   * Example:
   * ```
   * SELECT
   *     .RAW("SELECT id, name FROM author WHERE id > ?", 10L)
   *     .observes(AUTHOR)
   *     .readAs(SqlType.LONG, SqlType.STRING.nullable)
   * ```
   *
   * @param sql SQL SELECT statement
   * @param arguments Values bound to the `?` placeholders of the SQL: `null` or values of type `String`, `Long`,
   *     `Int`, `Short`, `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
   * @return Raw SQL SELECT statement builder
   * @throws IllegalArgumentException if an argument is a value of another type
   */
  @CheckResult
  fun RAW(sql: String, vararg arguments: Any?) = rawSelect(
    sql = sql,
    arguments = arguments
  )

  /**
   * Create raw SQL SELECT statement.
   *
   * Read the results with `readAs` or get a cursor with `toCursor`. Define the tables that the query reads with
   * `observes`, otherwise `observe` runs the query only once.
   *
   * Example:
   * ```
   * (SELECT RAW "PRAGMA user_version").readAs(SqlType.LONG)
   * ```
   *
   * @param sql SQL SELECT statement
   * @return Raw SQL SELECT statement builder
   */
  @CheckResult
  infix fun RAW(sql: String) = rawSelect(
    sql = sql,
    arguments = emptyArray()
  )

  /**
   * Select single column.
   *
   * Example:
   * ```
   * SELECT COLUMN AUTHOR.NAME FROM AUTHOR
   * ```
   *
   * @param expression Column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <R, N : SqlNullability, T> COLUMN(expression: SqlExpression<R, N, T>) = SelectProjectionStage(
    SelectNode(
      ScalarSelection(
        expression = expression,
        read = expression.selectRead
      )
    )
  )

  /**
   * Select two columns.
   *
   * Rows are returned as [Row2].
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
   *     .FROM(AUTHOR)
   * ```
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>
  ) = rowProjection(
    outputs = listOf(first, second),
    read = read2(
      readA = first.selectRead,
      readB = second.selectRead,
      create = ::Row2
    )
  )

  /**
   * Select three columns.
   *
   * Rows are returned as [Row3].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>
  ) = rowProjection(
    outputs = listOf(first, second, third),
    read = read3(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      create = ::Row3
    )
  )

  /**
   * Select four columns.
   *
   * Rows are returned as [Row4].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>
  ) = rowProjection(
    outputs = listOf(first, second, third, fourth),
    read = read4(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      readD = fourth.selectRead,
      create = ::Row4
    )
  )

  /**
   * Select five columns.
   *
   * Rows are returned as [Row5].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>
  ) = rowProjection(
    outputs = listOf(first, second, third, fourth, fifth),
    read = read5(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      readD = fourth.selectRead,
      readE = fifth.selectRead,
      create = ::Row5
    )
  )

  /**
   * Select six columns.
   *
   * Rows are returned as [Row6].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>
  ) = rowProjection(
    outputs = listOf(first, second, third, fourth, fifth, sixth),
    read = read6(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      readD = fourth.selectRead,
      readE = fifth.selectRead,
      readF = sixth.selectRead,
      create = ::Row6
    )
  )

  /**
   * Select seven columns.
   *
   * Rows are returned as [Row7].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @param seventh The seventh column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F, G> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>,
    seventh: SqlExpression<*, *, G>
  ) = rowProjection(
    outputs = listOf(first, second, third, fourth, fifth, sixth, seventh),
    read = read7(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      readD = fourth.selectRead,
      readE = fifth.selectRead,
      readF = sixth.selectRead,
      readG = seventh.selectRead,
      create = ::Row7
    )
  )

  /**
   * Select eight columns.
   *
   * Rows are returned as [Row8].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @param seventh The seventh column or expression to select
   * @param eighth The eighth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F, G, H> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>,
    seventh: SqlExpression<*, *, G>,
    eighth: SqlExpression<*, *, H>
  ) = rowProjection(
    outputs = listOf(first, second, third, fourth, fifth, sixth, seventh, eighth),
    read = read8(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      readD = fourth.selectRead,
      readE = fifth.selectRead,
      readF = sixth.selectRead,
      readG = seventh.selectRead,
      readH = eighth.selectRead,
      create = ::Row8
    )
  )

  /**
   * Select nine columns.
   *
   * Rows are returned as [Row9].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @param seventh The seventh column or expression to select
   * @param eighth The eighth column or expression to select
   * @param ninth The ninth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F, G, H, I> COLUMNS(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>,
    seventh: SqlExpression<*, *, G>,
    eighth: SqlExpression<*, *, H>,
    ninth: SqlExpression<*, *, I>
  ) = rowProjection(
    outputs = listOf(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth),
    read = read9(
      readA = first.selectRead,
      readB = second.selectRead,
      readC = third.selectRead,
      readD = fourth.selectRead,
      readE = fifth.selectRead,
      readF = sixth.selectRead,
      readG = seventh.selectRead,
      readH = eighth.selectRead,
      readI = ninth.selectRead,
      create = ::Row9
    )
  )

  /**
   * Select multiple columns.
   *
   * Rows are returned as [SelectedRow], whose values are read by the selected expressions, for example
   * `row[BOOK.ID]`. Use the `all()` of a table to select all of its columns.
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(BOOK.ID, AUTHOR.all())
   *     .FROM(BOOK)
   * ```
   *
   * @param outputs Columns, expressions and column lists to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun COLUMNS(vararg outputs: SqlOutput) = wideProjection(outputs)

  /**
   * Select all columns from [table].
   *
   * Rows are returned as the table's model objects.
   *
   * Example:
   * ```
   * SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS "Ada")
   * ```
   *
   * @param table Table to select from. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <M> FROM(table: Table<M>) = SelectFromStage(
    SelectNode(
      selection = EntitySelection(table),
      source = table
    )
  )

  /**
   * Select all columns from a subquery or a common table expression.
   *
   * Rows are returned as the query returns them.
   *
   * Example:
   * ```
   * SELECT FROM (ids AS "published")
   * ```
   *
   * @param source Query, aliased subquery or common table expression to select from
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <M> FROM(source: QueryOutputSource<M>) = source.occurrence.let { occurrence ->
    SelectFromStage(
      SelectNode(
        selection = QuerySelection(occurrence),
        source = occurrence
      )
    )
  }

  /**
   * Select distinct rows.
   *
   * Example:
   * ```
   * SELECT.DISTINCT FROM AUTHOR
   * ```
   *
   * @return SQL SELECT DISTINCT statement builder
   */
  val DISTINCT get() = SelectDistinct

  /**
   * Select single distinct column.
   *
   * Creates "SELECT DISTINCT `column` ..." query builder where duplicate rows
   * are removed from the set of result rows. For the purposes of detecting duplicate
   * rows, two NULL values are considered to be equal.
   *
   * Example:
   * ```
   * SELECT DISTINCT AUTHOR.NAME FROM AUTHOR
   * ```
   *
   * @param expression Column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <R, N : SqlNullability, T> DISTINCT(expression: SqlExpression<R, N, T>) = COLUMN(expression).distinct()

  /**
   * Select two distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row2].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>
  ) = COLUMNS(first, second).distinct()

  /**
   * Select three distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row3].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>
  ) = COLUMNS(first, second, third).distinct()

  /**
   * Select four distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row4].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>
  ) = COLUMNS(first, second, third, fourth).distinct()

  /**
   * Select five distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row5].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>
  ) = COLUMNS(first, second, third, fourth, fifth).distinct()

  /**
   * Select six distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row6].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth).distinct()

  /**
   * Select seven distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row7].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @param seventh The seventh column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F, G> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>,
    seventh: SqlExpression<*, *, G>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth, seventh).distinct()

  /**
   * Select eight distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row8].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @param seventh The seventh column or expression to select
   * @param eighth The eighth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F, G, H> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>,
    seventh: SqlExpression<*, *, G>,
    eighth: SqlExpression<*, *, H>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth, seventh, eighth).distinct()

  /**
   * Select nine distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row9].
   *
   * @param first The first column or expression to select
   * @param second The second column or expression to select
   * @param third The third column or expression to select
   * @param fourth The fourth column or expression to select
   * @param fifth The fifth column or expression to select
   * @param sixth The sixth column or expression to select
   * @param seventh The seventh column or expression to select
   * @param eighth The eighth column or expression to select
   * @param ninth The ninth column or expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <A, B, C, D, E, F, G, H, I> DISTINCT(
    first: SqlExpression<*, *, A>,
    second: SqlExpression<*, *, B>,
    third: SqlExpression<*, *, C>,
    fourth: SqlExpression<*, *, D>,
    fifth: SqlExpression<*, *, E>,
    sixth: SqlExpression<*, *, F>,
    seventh: SqlExpression<*, *, G>,
    eighth: SqlExpression<*, *, H>,
    ninth: SqlExpression<*, *, I>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth).distinct()

  /**
   * Select multiple distinct columns.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [SelectedRow], whose values are read by the selected expressions.
   *
   * @param outputs Columns, expressions and column lists to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun DISTINCT(vararg outputs: SqlOutput) = wideProjection(outputs).distinct()
}

/**
 * Create a new builder for SQL SELECT statement.
 *
 * Example:
 * ```
 * SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS "Ada")
 *
 * SELECT COLUMN AUTHOR.NAME FROM AUTHOR
 *
 * SELECT
 *     .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
 *     .FROM(AUTHOR)
 *     .ORDER_BY(AUTHOR.NAME.ASC())
 * ```
 *
 * @see [SQLite documentation: SELECT](https://www.sqlite.org/lang_select.html)
 */
val SELECT = Select

private fun rawSelect(sql: String, arguments: Array<out Any?>) = RawSelect<UndeclaredTables>(
  ExpressionNode.Raw(
    sql = sql,
    arguments = arguments.map(::nativeValue),
    dependencies = QueryDependencies.NONE
  )
)

/**
 * Builder for SQL SELECT DISTINCT statement.
 */
object SelectDistinct {
  /**
   * Select all distinct rows from [table].
   *
   * Creates "SELECT DISTINCT ... FROM ..." query builder where duplicate rows are removed from the set of result
   * rows. For the purposes of detecting duplicate rows, two NULL values are considered to be equal.
   *
   * Rows are returned as the table's model objects.
   *
   * @param table Table to select from. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <M> FROM(table: Table<M>) = Select.FROM(table).distinct()

  /**
   * Select all distinct rows from a subquery or a common table expression.
   *
   * Creates "SELECT DISTINCT ... FROM ..." query builder where duplicate rows are removed from the set of result
   * rows. For the purposes of detecting duplicate rows, two NULL values are considered to be equal.
   *
   * @param source Query, aliased subquery or common table expression to select from
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <M> FROM(source: QueryOutputSource<M>) = Select.FROM(source).distinct()
}

private fun <T> rowProjection(
  outputs: List<SqlExpression<*, *, *>>,
  read: (Cursor, Int) -> T
) = SelectProjectionStage(
  SelectNode(
    RowSelection(
      outputs = outputs,
      reader = { read }
    )
  )
)

private fun wideProjection(outputs: Array<out SqlOutput>) = SelectedOutputs(
  buildList(outputs.size) {
    outputs.forEach { output ->
      when (output) {
        is SqlExpression<*, *, *> -> add(output)
        is TableFields -> addAll(output.fields)
      }
    }
  }
)
  .let { selected ->
    rowProjection(
      outputs = selected.expressions,
      read = selected::ownedRow
    )
  }

private fun <T, S : Selection<T>> SelectProjectionStage<T, S>.distinct() = SelectProjectionStage(
  node.copy(distinct = true)
)

private fun <T, S : Selection<T>> SelectFromStage<T, S>.distinct() = SelectFromStage(node.copy(distinct = true))
