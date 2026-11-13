@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.InsertHead
import com.siimkinks.sqlitemagic.internal.sql.InsertedColumns
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
import com.siimkinks.sqlitemagic.internal.sql.withBuiltRow
import com.siimkinks.sqlitemagic.internal.sql.withBuiltRows
import com.siimkinks.sqlitemagic.internal.sql.withColumnArray
import com.siimkinks.sqlitemagic.internal.sql.withColumns
import com.siimkinks.sqlitemagic.internal.sql.withExpressionRow
import com.siimkinks.sqlitemagic.internal.sql.withHostRow
import com.siimkinks.sqlitemagic.internal.sql.withListedRows

/**
 * Builder for SQL INSERT statement.
 */
class InsertConflictStage internal constructor(
  private val conflictAlgorithm: Int
) {
  /**
   * Define a table to insert into.
   *
   * @param table Table to insert into. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if [table] is aliased
   */
  @CheckResult
  infix fun <P> INTO(table: Table<P>) = insertInto(
    table = table,
    conflictAlgorithm = conflictAlgorithm
  )
}

internal fun <P> insertInto(table: Table<P>, conflictAlgorithm: Int) = InsertIntoStage<P>(
  MutationNode.insert(
    table = table,
    conflictAlgorithm = conflictAlgorithm
  )
)

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 */
class InsertIntoStage<P> internal constructor(
  private val node: MutationNode<InsertHead>
) {
  /**
   * Define the column to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * Example:
   * ```
   * INSERT INTO AUTHOR COLUMNS AUTHOR.NAME VALUES "Ada"
   * ```
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  infix fun <R1, W1> COLUMNS(first: Column<P, R1, *, *, W1, *, *>) = InsertColumns1Stage<P, R1, W1>(
    node.withColumns(first)
  )

  /**
   * Define the two columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(BOOK)
   *     .COLUMNS(BOOK.TITLE, BOOK.PAGES)
   *     .VALUES("Dune", 412)
   * ```
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>
  ) = InsertColumns2Stage<P, R1, W1, R2, W2>(node.withColumns(first, second))

  /**
   * Define the three columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>
  ) = InsertColumns3Stage<P, R1, W1, R2, W2, R3, W3>(node.withColumns(first, second, third))

  /**
   * Define the four columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @param fourth The fourth column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3, R4, W4> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>,
    fourth: Column<P, R4, *, *, W4, *, *>
  ) = InsertColumns4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(node.withColumns(first, second, third, fourth))

  /**
   * Define the five columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @param fourth The fourth column to insert values into
   * @param fifth The fifth column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3, R4, W4, R5, W5> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>,
    fourth: Column<P, R4, *, *, W4, *, *>,
    fifth: Column<P, R5, *, *, W5, *, *>
  ) = InsertColumns5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(
    node.withColumns(first, second, third, fourth, fifth)
  )

  /**
   * Define the six columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @param fourth The fourth column to insert values into
   * @param fifth The fifth column to insert values into
   * @param sixth The sixth column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>,
    fourth: Column<P, R4, *, *, W4, *, *>,
    fifth: Column<P, R5, *, *, W5, *, *>,
    sixth: Column<P, R6, *, *, W6, *, *>
  ) = InsertColumns6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(
    node.withColumns(first, second, third, fourth, fifth, sixth)
  )

  /**
   * Define the seven columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @param fourth The fourth column to insert values into
   * @param fifth The fifth column to insert values into
   * @param sixth The sixth column to insert values into
   * @param seventh The seventh column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>,
    fourth: Column<P, R4, *, *, W4, *, *>,
    fifth: Column<P, R5, *, *, W5, *, *>,
    sixth: Column<P, R6, *, *, W6, *, *>,
    seventh: Column<P, R7, *, *, W7, *, *>
  ) = InsertColumns7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(
    node.withColumns(first, second, third, fourth, fifth, sixth, seventh)
  )

  /**
   * Define the eight columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @param fourth The fourth column to insert values into
   * @param fifth The fifth column to insert values into
   * @param sixth The sixth column to insert values into
   * @param seventh The seventh column to insert values into
   * @param eighth The eighth column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>,
    fourth: Column<P, R4, *, *, W4, *, *>,
    fifth: Column<P, R5, *, *, W5, *, *>,
    sixth: Column<P, R6, *, *, W6, *, *>,
    seventh: Column<P, R7, *, *, W7, *, *>,
    eighth: Column<P, R8, *, *, W8, *, *>
  ) = InsertColumns8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(
    node.withColumns(first, second, third, fourth, fifth, sixth, seventh, eighth)
  )

  /**
   * Define the nine columns to insert values into.
   *
   * Columns that the statement does not list take their default values.
   *
   * @param first The first column to insert values into. This param must be one of annotation
   *     processor generated column objects of the inserted table
   * @param second The second column to insert values into
   * @param third The third column to insert values into
   * @param fourth The fourth column to insert values into
   * @param fifth The fifth column to insert values into
   * @param sixth The sixth column to insert values into
   * @param seventh The seventh column to insert values into
   * @param eighth The eighth column to insert values into
   * @param ninth The ninth column to insert values into
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9> COLUMNS(
    first: Column<P, R1, *, *, W1, *, *>,
    second: Column<P, R2, *, *, W2, *, *>,
    third: Column<P, R3, *, *, W3, *, *>,
    fourth: Column<P, R4, *, *, W4, *, *>,
    fifth: Column<P, R5, *, *, W5, *, *>,
    sixth: Column<P, R6, *, *, W6, *, *>,
    seventh: Column<P, R7, *, *, W7, *, *>,
    eighth: Column<P, R8, *, *, W8, *, *>,
    ninth: Column<P, R9, *, *, W9, *, *>
  ) = InsertColumns9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(
    node.withColumns(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth)
  )

  /**
   * Define the columns to insert values into, for statements with more than nine columns.
   *
   * Columns that the statement does not list take their default values. Rows assign values to the columns by
   * name, for example `it[EVENT.KIND] = "tap"`, instead of by position.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(EVENT)
   *     .COLUMNS(EVENT.KIND, EVENT.AT, EVENT.USER, …, EVENT.OS_VERSION)
   *     .VALUES {
   *       it[EVENT.KIND] = "tap"
   *       it[EVENT.USER] = currentUser.asScalar()
   *       …
   *     }
   * ```
   *
   * @param columns The columns to insert values into. These params must be annotation processor generated column
   *     objects of the inserted table
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if a column is not a column of the inserted table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun COLUMNS(vararg columns: Column<P, *, *, *, *, *, *>) = InsertColumnsStage<P>(
    node = node.withColumnArray(columns),
    columns = InsertedColumns(
      table = node.head.table,
      columns = columns
    )
  )

  /**
   * Insert one row of default values.
   *
   * Every column takes its default value.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(BOOK)
   *     .DEFAULT_VALUES()
   *     .execute()
   * ```
   *
   * @return SQL INSERT statement
   */
  @CheckResult
  fun DEFAULT_VALUES() = InsertDefaultValuesStage(node)
}

/**
 * SQL INSERT statement of one row of default values, which can read the row ID of the inserted row.
 */
class InsertDefaultValuesStage internal constructor(
  private val node: MutationNode<InsertHead>
) : MutationQuery by MutationStatement(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * Example:
   * ```
   * val id = INSERT
   *     .INTO(BOOK)
   *     .DEFAULT_VALUES()
   *     .readRowId()
   *     .execute()
   * ```
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of one column.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 */
sealed class InsertRows1Stage<P, R1, W1>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @return SQL INSERT statement
   */
  @CheckResult
  open infix fun VALUES(first: W1): InsertValues1Stage<P, R1, W1> = InsertValues1Stage(node.withHostRow(first))

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @return SQL INSERT statement
   */
  @CheckResult
  open infix fun VALUES(first: SqlExpression<R1?, *, *>): InsertValues1Stage<P, R1, W1> =
      InsertValues1Stage(node.withExpressionRow(first))

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * Example:
   * ```
   * INSERT INTO AUTHOR COLUMNS AUTHOR.NAME VALUES names.map(::Row1)
   * ```
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(rows: List<Row1<W1>>) = InsertValues1Stage<P, R1, W1>(
    node.withListedRows(
      rows = rows,
      component = ROW1_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 */
class InsertColumns1Stage<P, R1, W1> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows1Stage<P, R1, W1>(node) {
  @CheckResult
  override infix fun VALUES(first: W1) = InsertSingleRow1Stage<P, R1, W1>(node.withHostRow(first))

  @CheckResult
  override infix fun VALUES(first: SqlExpression<R1?, *, *>) =
      InsertSingleRow1Stage<P, R1, W1>(node.withExpressionRow(first))
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 */
open class InsertValues1Stage<P, R1, W1> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows1Stage<P, R1, W1>(node), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 */
class InsertSingleRow1Stage<P, R1, W1> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues1Stage<P, R1, W1>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * Example:
   * ```
   * val id = INSERT
   *     .INTO(AUTHOR)
   *     .COLUMNS(AUTHOR.NAME)
   *     .VALUES("Ada")
   *     .readRowId()
   *     .execute()
   * ```
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of two columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 */
sealed class InsertRows2Stage<P, R1, W1, R2, W2>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2
  ): InsertValues2Stage<P, R1, W1, R2, W2> = InsertValues2Stage(node.withHostRow(first, second))

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>
  ): InsertValues2Stage<P, R1, W1, R2, W2> = InsertValues2Stage(node.withExpressionRow(first, second))

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(BOOK)
   *     .COLUMNS(BOOK.TITLE, BOOK.PAGES)
   *     .VALUES(books.map { Row2(it.title, it.pages) })
   * ```
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(rows: List<Row2<W1, W2>>) = InsertValues2Stage<P, R1, W1, R2, W2>(
    node.withListedRows(
      rows = rows,
      component = ROW2_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 */
class InsertColumns2Stage<P, R1, W1, R2, W2> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows2Stage<P, R1, W1, R2, W2>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2
  ) = InsertSingleRow2Stage<P, R1, W1, R2, W2>(node.withHostRow(first, second))

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>
  ) = InsertSingleRow2Stage<P, R1, W1, R2, W2>(node.withExpressionRow(first, second))
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 */
open class InsertValues2Stage<P, R1, W1, R2, W2> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows2Stage<P, R1, W1, R2, W2>(node), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 */
class InsertSingleRow2Stage<P, R1, W1, R2, W2> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues2Stage<P, R1, W1, R2, W2>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of three columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 */
sealed class InsertRows3Stage<P, R1, W1, R2, W2, R3, W3>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3
  ): InsertValues3Stage<P, R1, W1, R2, W2, R3, W3> = InsertValues3Stage(node.withHostRow(first, second, third))

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>
  ): InsertValues3Stage<P, R1, W1, R2, W2, R3, W3> = InsertValues3Stage(node.withExpressionRow(first, second, third))

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(rows: List<Row3<W1, W2, W3>>) = InsertValues3Stage<P, R1, W1, R2, W2, R3, W3>(
    node.withListedRows(
      rows = rows,
      component = ROW3_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 */
class InsertColumns3Stage<P, R1, W1, R2, W2, R3, W3> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows3Stage<P, R1, W1, R2, W2, R3, W3>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3
  ) = InsertSingleRow3Stage<P, R1, W1, R2, W2, R3, W3>(node.withHostRow(first, second, third))

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>
  ) = InsertSingleRow3Stage<P, R1, W1, R2, W2, R3, W3>(node.withExpressionRow(first, second, third))
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 */
open class InsertValues3Stage<P, R1, W1, R2, W2, R3, W3> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows3Stage<P, R1, W1, R2, W2, R3, W3>(node), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 */
class InsertSingleRow3Stage<P, R1, W1, R2, W2, R3, W3> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues3Stage<P, R1, W1, R2, W2, R3, W3>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of four columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 */
sealed class InsertRows4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @param fourth Value of the fourth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4
  ): InsertValues4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4> =
      InsertValues4Stage(node.withHostRow(first, second, third, fourth))

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @param fourth Expression whose value to insert into the fourth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>
  ): InsertValues4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4> =
      InsertValues4Stage(node.withExpressionRow(first, second, third, fourth))

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(rows: List<Row4<W1, W2, W3, W4>>) = InsertValues4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(
    node.withListedRows(
      rows = rows,
      component = ROW4_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 */
class InsertColumns4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4
  ) = InsertSingleRow4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(node.withHostRow(first, second, third, fourth))

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>
  ) = InsertSingleRow4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(node.withExpressionRow(first, second, third, fourth))
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 */
open class InsertValues4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(node), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 */
class InsertSingleRow4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues4Stage<P, R1, W1, R2, W2, R3, W3, R4, W4>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of five columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 */
sealed class InsertRows5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @param fourth Value of the fourth column
   * @param fifth Value of the fifth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5
  ): InsertValues5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5> = InsertValues5Stage(
    node.withHostRow(first, second, third, fourth, fifth)
  )

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @param fourth Expression whose value to insert into the fourth column
   * @param fifth Expression whose value to insert into the fifth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>
  ): InsertValues5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5> = InsertValues5Stage(
    node.withExpressionRow(first, second, third, fourth, fifth)
  )

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(
    rows: List<Row5<W1, W2, W3, W4, W5>>
  ) = InsertValues5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(
    node.withListedRows(
      rows = rows,
      component = ROW5_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 */
class InsertColumns5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5
  ) = InsertSingleRow5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(
    node.withHostRow(first, second, third, fourth, fifth)
  )

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>
  ) = InsertSingleRow5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(
    node.withExpressionRow(first, second, third, fourth, fifth)
  )
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 */
open class InsertValues5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(node), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 */
class InsertSingleRow5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues5Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of six columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 */
sealed class InsertRows6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @param fourth Value of the fourth column
   * @param fifth Value of the fifth column
   * @param sixth Value of the sixth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6
  ): InsertValues6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6> = InsertValues6Stage(
    node.withHostRow(first, second, third, fourth, fifth, sixth)
  )

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @param fourth Expression whose value to insert into the fourth column
   * @param fifth Expression whose value to insert into the fifth column
   * @param sixth Expression whose value to insert into the sixth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>
  ): InsertValues6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6> = InsertValues6Stage(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth)
  )

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(
    rows: List<Row6<W1, W2, W3, W4, W5, W6>>
  ) = InsertValues6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(
    node.withListedRows(
      rows = rows,
      component = ROW6_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 */
class InsertColumns6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6
  ) = InsertSingleRow6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(
    node.withHostRow(first, second, third, fourth, fifth, sixth)
  )

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>
  ) = InsertSingleRow6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth)
  )
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 */
open class InsertValues6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(node), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 */
class InsertSingleRow6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues6Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of seven columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 */
sealed class InsertRows7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @param fourth Value of the fourth column
   * @param fifth Value of the fifth column
   * @param sixth Value of the sixth column
   * @param seventh Value of the seventh column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6,
    seventh: W7
  ): InsertValues7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7> = InsertValues7Stage(
    node.withHostRow(first, second, third, fourth, fifth, sixth, seventh)
  )

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @param fourth Expression whose value to insert into the fourth column
   * @param fifth Expression whose value to insert into the fifth column
   * @param sixth Expression whose value to insert into the sixth column
   * @param seventh Expression whose value to insert into the seventh column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>,
    seventh: SqlExpression<R7?, *, *>
  ): InsertValues7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7> = InsertValues7Stage(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth, seventh)
  )

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(
    rows: List<Row7<W1, W2, W3, W4, W5, W6, W7>>
  ) = InsertValues7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(
    node.withListedRows(
      rows = rows,
      component = ROW7_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 */
class InsertColumns7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6,
    seventh: W7
  ) = InsertSingleRow7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(
    node.withHostRow(first, second, third, fourth, fifth, sixth, seventh)
  )

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>,
    seventh: SqlExpression<R7?, *, *>
  ) = InsertSingleRow7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth, seventh)
  )
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 */
open class InsertValues7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(node),
  MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 */
class InsertSingleRow7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues7Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of eight columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 */
sealed class InsertRows8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @param fourth Value of the fourth column
   * @param fifth Value of the fifth column
   * @param sixth Value of the sixth column
   * @param seventh Value of the seventh column
   * @param eighth Value of the eighth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6,
    seventh: W7,
    eighth: W8
  ): InsertValues8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8> = InsertValues8Stage(
    node.withHostRow(first, second, third, fourth, fifth, sixth, seventh, eighth)
  )

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @param fourth Expression whose value to insert into the fourth column
   * @param fifth Expression whose value to insert into the fifth column
   * @param sixth Expression whose value to insert into the sixth column
   * @param seventh Expression whose value to insert into the seventh column
   * @param eighth Expression whose value to insert into the eighth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>,
    seventh: SqlExpression<R7?, *, *>,
    eighth: SqlExpression<R8?, *, *>
  ): InsertValues8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8> = InsertValues8Stage(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth, seventh, eighth)
  )

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(
    rows: List<Row8<W1, W2, W3, W4, W5, W6, W7, W8>>
  ) = InsertValues8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(
    node.withListedRows(
      rows = rows,
      component = ROW8_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 */
class InsertColumns8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6,
    seventh: W7,
    eighth: W8
  ) = InsertSingleRow8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(
    node.withHostRow(first, second, third, fourth, fifth, sixth, seventh, eighth)
  )

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>,
    seventh: SqlExpression<R7?, *, *>,
    eighth: SqlExpression<R8?, *, *>
  ) = InsertSingleRow8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth, seventh, eighth)
  )
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 */
open class InsertValues8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8> internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(node),
  MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 */
class InsertSingleRow8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8> internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues8Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of nine columns.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 * @param R9 Kotlin type of the ninth column values
 * @param W9 Kotlin type of the values inserted into the ninth column
 */
sealed class InsertRows9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Add a row of values.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   *
   * @param first Value of the first column
   * @param second Value of the second column
   * @param third Value of the third column
   * @param fourth Value of the fourth column
   * @param fifth Value of the fifth column
   * @param sixth Value of the sixth column
   * @param seventh Value of the seventh column
   * @param eighth Value of the eighth column
   * @param ninth Value of the ninth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6,
    seventh: W7,
    eighth: W8,
    ninth: W9
  ): InsertValues9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9> = InsertValues9Stage(
    node.withHostRow(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth)
  )

  /**
   * Add a row of expressions, such as subqueries, parameters, functions or `asValue` values.
   *
   * Each expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param first Expression whose value to insert into the first column
   * @param second Expression whose value to insert into the second column
   * @param third Expression whose value to insert into the third column
   * @param fourth Expression whose value to insert into the fourth column
   * @param fifth Expression whose value to insert into the fifth column
   * @param sixth Expression whose value to insert into the sixth column
   * @param seventh Expression whose value to insert into the seventh column
   * @param eighth Expression whose value to insert into the eighth column
   * @param ninth Expression whose value to insert into the ninth column
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>,
    seventh: SqlExpression<R7?, *, *>,
    eighth: SqlExpression<R8?, *, *>,
    ninth: SqlExpression<R9?, *, *>
  ): InsertValues9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9> = InsertValues9Stage(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth)
  )

  /**
   * Add a row for each element of a list.
   *
   * Values are converted to SQL in the same way as the columns' other values, including the columns' transformers.
   * An empty list adds no rows, and a statement whose rows all come from empty lists inserts nothing without
   * running any SQL. Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one
   * statement. Statements with list rows are prepared without the connection's statement cache.
   *
   * @param rows Rows of values, in column order
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun VALUES(
    rows: List<Row9<W1, W2, W3, W4, W5, W6, W7, W8, W9>>
  ) = InsertValues9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(
    node.withListedRows(
      rows = rows,
      component = ROW9_VALUES
    )
  )
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 * @param R9 Kotlin type of the ninth column values
 * @param W9 Kotlin type of the values inserted into the ninth column
 */
class InsertColumns9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>
    internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(node) {
  @CheckResult
  override fun VALUES(
    first: W1,
    second: W2,
    third: W3,
    fourth: W4,
    fifth: W5,
    sixth: W6,
    seventh: W7,
    eighth: W8,
    ninth: W9
  ) = InsertSingleRow9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(
    node.withHostRow(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth)
  )

  @CheckResult
  override fun VALUES(
    first: SqlExpression<R1?, *, *>,
    second: SqlExpression<R2?, *, *>,
    third: SqlExpression<R3?, *, *>,
    fourth: SqlExpression<R4?, *, *>,
    fifth: SqlExpression<R5?, *, *>,
    sixth: SqlExpression<R6?, *, *>,
    seventh: SqlExpression<R7?, *, *>,
    eighth: SqlExpression<R8?, *, *>,
    ninth: SqlExpression<R9?, *, *>
  ) = InsertSingleRow9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(
    node.withExpressionRow(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth)
  )
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 * @param R9 Kotlin type of the ninth column values
 * @param W9 Kotlin type of the values inserted into the ninth column
 */
open class InsertValues9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>
    internal constructor(
  node: MutationNode<InsertHead>
) : InsertRows9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(node),
  MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 * @param R1 Kotlin type of the first column values
 * @param W1 Kotlin type of the values inserted into the first column
 * @param R2 Kotlin type of the second column values
 * @param W2 Kotlin type of the values inserted into the second column
 * @param R3 Kotlin type of the third column values
 * @param W3 Kotlin type of the values inserted into the third column
 * @param R4 Kotlin type of the fourth column values
 * @param W4 Kotlin type of the values inserted into the fourth column
 * @param R5 Kotlin type of the fifth column values
 * @param W5 Kotlin type of the values inserted into the fifth column
 * @param R6 Kotlin type of the sixth column values
 * @param W6 Kotlin type of the values inserted into the sixth column
 * @param R7 Kotlin type of the seventh column values
 * @param W7 Kotlin type of the values inserted into the seventh column
 * @param R8 Kotlin type of the eighth column values
 * @param W8 Kotlin type of the values inserted into the eighth column
 * @param R9 Kotlin type of the ninth column values
 * @param W9 Kotlin type of the values inserted into the ninth column
 */
class InsertSingleRow9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>
    internal constructor(
  node: MutationNode<InsertHead>
) : InsertValues9Stage<P, R1, W1, R2, W2, R3, W3, R4, W4, R5, W5, R6, W6, R7, W7, R8, W8, R9, W9>(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for SQL INSERT statement that inserts rows of the columns listed with `COLUMNS(vararg columns)`.
 *
 * @param P Inserted table model type
 */
sealed class InsertRowsStage<P>(
  internal val node: MutationNode<InsertHead>,
  internal val columns: InsertedColumns
) {
  /**
   * Add a row.
   *
   * [row] assigns a value to every listed column, for example `it[EVENT.KIND] = "tap"`. Values are converted to SQL
   * in the same way as the columns' other values, including the columns' transformers.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(EVENT)
   *     .COLUMNS(EVENT.KIND, EVENT.AT, EVENT.USER, …, EVENT.OS_VERSION)
   *     .VALUES {
   *       it[EVENT.KIND] = "tap"
   *       it[EVENT.USER] = currentUser.asScalar()
   *       …
   *     }
   * ```
   *
   * @param row Assigns the values of the row
   * @return SQL INSERT statement
   * @throws IllegalArgumentException if [row] assigns a column that the statement does not list, or leaves a listed
   *     column without a value
   */
  @CheckResult
  open infix fun VALUES(row: (InsertedRow<P>) -> Unit) = InsertValuesStage<P>(
    node = builtRow(row),
    columns = columns
  )

  /**
   * Add a row for each element of a list.
   *
   * [row] assigns a value to every listed column for an element, for example `row[EVENT.KIND] = event.kind`. Values
   * are converted to SQL in the same way as the columns' other values, including the columns' transformers. An empty
   * list adds no rows, and a statement whose rows all come from empty lists inserts nothing without running any SQL.
   * Each value binds a variable, and SQLite before 3.32.0 rejects more than 999 variables in one statement.
   * Statements with list rows are prepared without the connection's statement cache.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(EVENT)
   *     .COLUMNS(EVENT.KIND, EVENT.AT, EVENT.USER, …, EVENT.OS_VERSION)
   *     .VALUES(events) { row, event ->
   *       row[EVENT.KIND] = event.kind
   *       …
   *     }
   * ```
   *
   * @param rows Elements to add rows for
   * @param row Assigns the values of the row of an element
   * @return SQL INSERT statement
   * @throws IllegalArgumentException if [row] assigns a column that the statement does not list, or leaves a listed
   *     column without a value
   */
  @CheckResult
  fun <T> VALUES(rows: List<T>, row: (InsertedRow<P>, T) -> Unit) = InsertValuesStage<P>(
    node = InsertedRow<P>(
      columns = columns,
      count = rows.size
    ).let { builder ->
      rows.forEach { element ->
        row(builder, element)
        builder.next()
      }
      node.withBuiltRows(
        values = builder.values,
        width = columns.size,
        count = rows.size
      )
    },
    columns = columns
  )

  internal fun builtRow(row: (InsertedRow<P>) -> Unit) = InsertedRow<P>(
    columns = columns,
    count = 1
  ).let { builder ->
    row(builder)
    builder.next()
    node.withBuiltRow(builder.values)
  }
}

/**
 * Builder for SQL INSERT statement.
 *
 * @param P Inserted table model type
 */
class InsertColumnsStage<P> internal constructor(
  node: MutationNode<InsertHead>,
  columns: InsertedColumns
) : InsertRowsStage<P>(
  node = node,
  columns = columns
) {
  @CheckResult
  override infix fun VALUES(row: (InsertedRow<P>) -> Unit) = InsertSingleRowStage<P>(
    node = builtRow(row),
    columns = columns
  )
}

/**
 * SQL INSERT statement, which can take more rows.
 *
 * @param P Inserted table model type
 */
open class InsertValuesStage<P> internal constructor(
  node: MutationNode<InsertHead>,
  columns: InsertedColumns
) : InsertRowsStage<P>(
  node = node,
  columns = columns
), MutationQuery by MutationStatement(node)

/**
 * SQL INSERT statement of one row, which can take more rows or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 */
class InsertSingleRowStage<P> internal constructor(
  node: MutationNode<InsertHead>,
  columns: InsertedColumns
) : InsertValuesStage<P>(
  node = node,
  columns = columns
) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that `CONFLICT_IGNORE` ignores.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

private val ROW1_VALUES: (Row1<*>, Int) -> Any? = { row, _ -> row.first }

private val ROW2_VALUES: (Row2<*, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    else -> row.second
  }
}

private val ROW3_VALUES: (Row3<*, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    else -> row.third
  }
}

private val ROW4_VALUES: (Row4<*, *, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    2 -> row.third
    else -> row.fourth
  }
}

private val ROW5_VALUES: (Row5<*, *, *, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    2 -> row.third
    3 -> row.fourth
    else -> row.fifth
  }
}

private val ROW6_VALUES: (Row6<*, *, *, *, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    2 -> row.third
    3 -> row.fourth
    4 -> row.fifth
    else -> row.sixth
  }
}

private val ROW7_VALUES: (Row7<*, *, *, *, *, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    2 -> row.third
    3 -> row.fourth
    4 -> row.fifth
    5 -> row.sixth
    else -> row.seventh
  }
}

private val ROW8_VALUES: (Row8<*, *, *, *, *, *, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    2 -> row.third
    3 -> row.fourth
    4 -> row.fifth
    5 -> row.sixth
    6 -> row.seventh
    else -> row.eighth
  }
}

private val ROW9_VALUES: (Row9<*, *, *, *, *, *, *, *, *>, Int) -> Any? = { row, index ->
  when (index) {
    0 -> row.first
    1 -> row.second
    2 -> row.third
    3 -> row.fourth
    4 -> row.fifth
    5 -> row.sixth
    6 -> row.seventh
    7 -> row.eighth
    else -> row.ninth
  }
}
