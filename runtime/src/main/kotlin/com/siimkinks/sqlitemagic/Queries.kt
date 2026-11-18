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
   * Select the entity that a subquery or a common table expression selects.
   *
   * Rows are returned as the entity's model objects, read with the related objects that the subquery reads. Select
   * the entity of a table with `SELECT FROM` the table instead.
   *
   * Example:
   * ```
   * SELECT COLUMN totals[AUTHOR] FROM totals WHERE (totals[bookCount] GREATER_THAN 1L)
   * ```
   *
   * @param entity Entity of a subquery or a common table expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <M> COLUMN(entity: DerivedEntity<M>) = rowProjection<EntityRows, M>(
    values = listOf(entity),
    assemble = { reads -> reads.read(0) }
  )

  /**
   * Select two values.
   *
   * Rows are returned as [Row2]. A value is a column, an expression or a table, whose entity is read from the columns
   * that `SELECT FROM` the table selects.
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
   *     .FROM(AUTHOR)
   *
   * (
   *     SELECT.COLUMNS(AUTHOR, COUNT(BOOK.ID))
   *         FROM AUTHOR
   *         LEFT_JOIN BOOK ON (BOOK.AUTHOR IS AUTHOR.ID)
   *         GROUP_BY AUTHOR.ID
   *     )
   *   .mapTo(::AuthorWithCount)
   * ```
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>
  ) = rowProjection<Row2<RA, RB>, Row2<A, B>>(
    values = listOf(first, second),
    assemble = { reads -> reads.read2(::Row2) }
  )

  /**
   * Select three values.
   *
   * Rows are returned as [Row3].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>
  ) = rowProjection<Row3<RA, RB, RC>, Row3<A, B, C>>(
    values = listOf(first, second, third),
    assemble = { reads -> reads.read3(::Row3) }
  )

  /**
   * Select four values.
   *
   * Rows are returned as [Row4].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>
  ) = rowProjection<Row4<RA, RB, RC, RD>, Row4<A, B, C, D>>(
    values = listOf(first, second, third, fourth),
    assemble = { reads -> reads.read4(::Row4) }
  )

  /**
   * Select five values.
   *
   * Rows are returned as [Row5].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>
  ) = rowProjection<Row5<RA, RB, RC, RD, RE>, Row5<A, B, C, D, E>>(
    values = listOf(first, second, third, fourth, fifth),
    assemble = { reads -> reads.read5(::Row5) }
  )

  /**
   * Select six values.
   *
   * Rows are returned as [Row6].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>
  ) = rowProjection<Row6<RA, RB, RC, RD, RE, RF>, Row6<A, B, C, D, E, F>>(
    values = listOf(first, second, third, fourth, fifth, sixth),
    assemble = { reads -> reads.read6(::Row6) }
  )

  /**
   * Select seven values.
   *
   * Rows are returned as [Row7].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @param seventh The seventh column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>,
    seventh: SelectValue<RG, G>
  ) = rowProjection<Row7<RA, RB, RC, RD, RE, RF, RG>, Row7<A, B, C, D, E, F, G>>(
    values = listOf(first, second, third, fourth, fifth, sixth, seventh),
    assemble = { reads -> reads.read7(::Row7) }
  )

  /**
   * Select eight values.
   *
   * Rows are returned as [Row8].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @param seventh The seventh column, expression or entity to select
   * @param eighth The eighth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>,
    seventh: SelectValue<RG, G>,
    eighth: SelectValue<RH, H>
  ) = rowProjection<Row8<RA, RB, RC, RD, RE, RF, RG, RH>, Row8<A, B, C, D, E, F, G, H>>(
    values = listOf(first, second, third, fourth, fifth, sixth, seventh, eighth),
    assemble = { reads -> reads.read8(::Row8) }
  )

  /**
   * Select nine values.
   *
   * Rows are returned as [Row9].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @param seventh The seventh column, expression or entity to select
   * @param eighth The eighth column, expression or entity to select
   * @param ninth The ninth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H, RI, I> COLUMNS(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>,
    seventh: SelectValue<RG, G>,
    eighth: SelectValue<RH, H>,
    ninth: SelectValue<RI, I>
  ) = rowProjection<Row9<RA, RB, RC, RD, RE, RF, RG, RH, RI>, Row9<A, B, C, D, E, F, G, H, I>>(
    values = listOf(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth),
    assemble = { reads -> reads.read9(::Row9) }
  )

  /**
   * Select multiple values.
   *
   * Rows are returned as [SelectedRow], whose values are read by the selected expressions and entities, for example
   * `row[BOOK.ID]` and `row[AUTHOR]`. Use the `all()` of a table to select all of its columns.
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(BOOK.ID, AUTHOR.all())
   *     .FROM(BOOK)
   * ```
   *
   * @param outputs Columns, expressions, entities and column lists to select
   * @return SQL SELECT statement builder
   * @throws IllegalArgumentException if an entity is the only selected value
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
   * Select the distinct entities that a subquery or a common table expression selects.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned as
   * the entity's model objects.
   *
   * Example:
   * ```
   * SELECT DISTINCT shelf[AUTHOR] FROM shelf
   * ```
   *
   * @param entity Entity of a subquery or a common table expression to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  infix fun <M> DISTINCT(entity: DerivedEntity<M>) = COLUMN(entity).distinct()

  /**
   * Select two distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row2].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>
  ) = COLUMNS(first, second).distinct()

  /**
   * Select three distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row3].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>
  ) = COLUMNS(first, second, third).distinct()

  /**
   * Select four distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row4].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>
  ) = COLUMNS(first, second, third, fourth).distinct()

  /**
   * Select five distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row5].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>
  ) = COLUMNS(first, second, third, fourth, fifth).distinct()

  /**
   * Select six distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row6].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth).distinct()

  /**
   * Select seven distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row7].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @param seventh The seventh column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>,
    seventh: SelectValue<RG, G>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth, seventh).distinct()

  /**
   * Select eight distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row8].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @param seventh The seventh column, expression or entity to select
   * @param eighth The eighth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>,
    seventh: SelectValue<RG, G>,
    eighth: SelectValue<RH, H>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth, seventh, eighth).distinct()

  /**
   * Select nine distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [Row9].
   *
   * @param first The first column, expression or entity to select
   * @param second The second column, expression or entity to select
   * @param third The third column, expression or entity to select
   * @param fourth The fourth column, expression or entity to select
   * @param fifth The fifth column, expression or entity to select
   * @param sixth The sixth column, expression or entity to select
   * @param seventh The seventh column, expression or entity to select
   * @param eighth The eighth column, expression or entity to select
   * @param ninth The ninth column, expression or entity to select
   * @return SQL SELECT statement builder
   */
  @CheckResult
  fun <RA, A, RB, B, RC, C, RD, D, RE, E, RF, F, RG, G, RH, H, RI, I> DISTINCT(
    first: SelectValue<RA, A>,
    second: SelectValue<RB, B>,
    third: SelectValue<RC, C>,
    fourth: SelectValue<RD, D>,
    fifth: SelectValue<RE, E>,
    sixth: SelectValue<RF, F>,
    seventh: SelectValue<RG, G>,
    eighth: SelectValue<RH, H>,
    ninth: SelectValue<RI, I>
  ) = COLUMNS(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth).distinct()

  /**
   * Select multiple distinct values.
   *
   * Creates "SELECT DISTINCT ..." query builder where duplicate rows are removed from the set of result rows.
   * For the purposes of detecting duplicate rows, two NULL values are considered to be equal. Rows are returned
   * as [SelectedRow], whose values are read by the selected expressions and entities.
   *
   * @param outputs Columns, expressions, entities and column lists to select
   * @return SQL SELECT statement builder
   * @throws IllegalArgumentException if an entity is the only selected value
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
  values: List<SelectValue<*, *>>,
  assemble: (SlotReads) -> ValueRead<T>
) = SelectProjectionStage(
  SelectNode(
    RowSelection<R, T>(
      values = values,
      reader = typedRowReader(assemble)
    )
  )
)

private fun wideProjection(outputs: Array<out SqlOutput>) = SelectProjectionStage(
  SelectNode(
    RowSelection<SelectedRow, SelectedRow>(
      values = buildList(outputs.size) {
        outputs.forEach { output ->
          when (output) {
            is SelectValue<*, *> -> add(output)
            is TableFields -> output.addTo(this)
          }
        }
      }.also(::requireNotOneEntity),
      reader = ::OwnedRowsReader
    )
  )
)

private fun requireNotOneEntity(values: List<SelectValue<*, *>>) {
  val value = values.singleOrNull()
  val entity = value?.entity ?: return
  val advice = when {
    value is NullableEntity<*> -> "select another value beside it"
    entity is DerivedEntity<*> -> "select it with SELECT COLUMN ${entity.reference}"
    else -> "select one entity with SELECT FROM ${entity.reference}"
  }
  throw IllegalArgumentException("The ${entity.label} is the only selected value; $advice")
}

private fun <T, S : Selection<T>> SelectProjectionStage<T, S>.distinct() = SelectProjectionStage(
  node.copy(distinct = true)
)

private fun <T, S : Selection<T>> SelectFromStage<T, S>.distinct() = SelectFromStage(node.copy(distinct = true))
