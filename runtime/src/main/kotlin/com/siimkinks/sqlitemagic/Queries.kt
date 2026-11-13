@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.ValueRead
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
   * Read the results with `readAs`, or with `useRows` when the columns are known only when the query runs. Define
   * the tables that the query reads with `observes`, otherwise `observe` runs the query only once. A connection
   * keeps the columns of `*` that it first prepared, so name the columns of a table that the app alters at runtime.
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
   * Read the results with `readAs`, or with `useRows` when the columns are known only when the query runs. Define
   * the tables that the query reads with `observes`, otherwise `observe` runs the query only once. A connection
   * keeps the columns of `*` that it first prepared, so name the columns of a table that the app alters at runtime.
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
  fun <RA, A, RB, B> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>
  ) = rowProjection<Row2<RA, RB>, _>(
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
  fun <RA, A, RB, B, RC, C> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>
  ) = rowProjection<Row3<RA, RB, RC>, _>(
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
  fun <RA, A, RB, B, RC, C, RD, D> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>
  ) = rowProjection<Row4<RA, RB, RC, RD>, _>(
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>
  ) = rowProjection<Row5<RA, RB, RC, RD, RE>, _>(
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>
  ) = rowProjection<Row6<RA, RB, RC, RD, RE, RF>, _>(
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>,
    seventh: SqlExpression<RG, *, G>
  ) = rowProjection<Row7<RA, RB, RC, RD, RE, RF, RG>, _>(
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>,
    seventh: SqlExpression<RG, *, G>,
    eighth: SqlExpression<RH, *, H>
  ) = rowProjection<Row8<RA, RB, RC, RD, RE, RF, RG, RH>, _>(
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H, RI, I> COLUMNS(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>,
    seventh: SqlExpression<RG, *, G>,
    eighth: SqlExpression<RH, *, H>,
    ninth: SqlExpression<RI, *, I>
  ) = rowProjection<Row9<RA, RB, RC, RD, RE, RF, RG, RH, RI>, _>(
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
  fun <RA, A, RB, B> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>
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
  fun <RA, A, RB, B, RC, C> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>
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
  fun <RA, A, RB, B, RC, C, RD, D> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>,
    seventh: SqlExpression<RG, *, G>
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>,
    seventh: SqlExpression<RG, *, G>,
    eighth: SqlExpression<RH, *, H>
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
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H, RI, I> DISTINCT(
    first: SqlExpression<RA, *, A>,
    second: SqlExpression<RB, *, B>,
    third: SqlExpression<RC, *, C>,
    fourth: SqlExpression<RD, *, D>,
    fifth: SqlExpression<RE, *, E>,
    sixth: SqlExpression<RF, *, F>,
    seventh: SqlExpression<RG, *, G>,
    eighth: SqlExpression<RH, *, H>,
    ninth: SqlExpression<RI, *, I>
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

private fun <R, T> rowProjection(
  outputs: List<SqlExpression<*, *, *>>,
  read: ValueRead<T>
) = SelectProjectionStage(
  SelectNode(
    RowSelection<R, T>(
      outputs = outputs,
      reader = { read }
    )
  )
)

private fun wideProjection(outputs: Array<out SqlOutput>) = buildList(outputs.size) {
  outputs.forEach { output ->
    when (output) {
      is SqlExpression<*, *, *> -> add(output)
      is TableFields -> addAll(output.fields)
    }
  }
}
  .let { expressions ->
    SelectProjectionStage(
      SelectNode(
        RowSelection<SelectedRow, SelectedRow>(
          outputs = expressions,
          reader = OwnedRowsReader(expressions)
        )
      )
    )
  }

private fun <T, S : Selection<T>> SelectProjectionStage<T, S>.distinct() = SelectProjectionStage(
  node.copy(distinct = true)
)

private fun <T, S : Selection<T>> SelectFromStage<T, S>.distinct() = SelectFromStage(node.copy(distinct = true))
