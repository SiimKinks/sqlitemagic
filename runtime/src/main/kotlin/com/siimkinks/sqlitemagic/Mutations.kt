@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
import com.siimkinks.sqlitemagic.internal.sql.nativeValue

/**
 * Builder for SQL UPDATE statement.
 *
 * Start statements with [UPDATE].
 */
object Update {
  /**
   * Define a table to update.
   *
   * Example:
   * ```
   * UPDATE TABLE AUTHOR SET (AUTHOR.NAME to "Ada") WHERE (AUTHOR.ID IS 1L)
   * ```
   *
   * @param table Table to update. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if [table] is aliased
   */
  @CheckResult
  infix fun <P> TABLE(table: Table<P>) = updateTable(
    table = table,
    conflictAlgorithm = CONFLICT_NONE
  )

  /**
   * Define a conflict algorithm for the UPDATE statement.
   *
   * Example:
   * ```
   * UPDATE OR CONFLICT_IGNORE TABLE AUTHOR SET (AUTHOR.NAME to "Ada")
   * ```
   *
   * @param conflictAlgorithm Conflict resolution algorithm to use
   * @return SQL UPDATE statement builder
   */
  @CheckResult
  infix fun OR(@ConflictAlgorithm conflictAlgorithm: Int) = UpdateConflictStage(conflictAlgorithm)

  /**
   * Create raw SQL UPDATE statement.
   *
   * Define the tables that the statement changes with `notifies`, so that observed queries of these tables are
   * re-run.
   *
   * Example:
   * ```
   * UPDATE
   *     .RAW("UPDATE author SET name = ? WHERE id = ?", "Ada", 1L)
   *     .notifies(AUTHOR)
   *     .execute()
   * ```
   *
   * @param sql SQL UPDATE statement
   * @param arguments Values bound to the `?` placeholders of the SQL: `null` or values of type `String`, `Long`,
   *     `Int`, `Short`, `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
   * @return Raw SQL UPDATE statement
   * @throws IllegalArgumentException if an argument is a value of another type
   */
  @CheckResult
  fun RAW(sql: String, vararg arguments: Any?) = rawStatement(
    sql = sql,
    arguments = arguments
  )

  /**
   * Create raw SQL UPDATE statement.
   *
   * Define the tables that the statement changes with `notifies`, so that observed queries of these tables are
   * re-run.
   *
   * Example:
   * ```
   * (UPDATE RAW "UPDATE author SET name = NULL")
   *     .notifies(AUTHOR)
   *     .execute()
   * ```
   *
   * @param sql SQL UPDATE statement
   * @return Raw SQL UPDATE statement
   */
  @CheckResult
  infix fun RAW(sql: String) = rawStatement(
    sql = sql,
    arguments = emptyArray()
  )
}

/**
 * Create a new builder for SQL UPDATE statement.
 *
 * Example:
 * ```
 * UPDATE TABLE AUTHOR SET (AUTHOR.NAME to "Ada") WHERE (AUTHOR.ID IS 1L)
 *
 * UPDATE
 *     .TABLE(BOOK)
 *     .SET(BOOK.PAGES, BOOK.PAGES + 1)
 *     .execute()
 * ```
 *
 * @see [SQLite documentation: UPDATE](https://www.sqlite.org/lang_update.html)
 */
val UPDATE = Update

/**
 * Builder for SQL DELETE statement.
 *
 * Start statements with [DELETE].
 */
object Delete {
  /**
   * Define a table to delete from.
   *
   * Without WHERE clause all rows are deleted.
   *
   * Example:
   * ```
   * DELETE FROM AUTHOR WHERE (AUTHOR.NAME IS "George")
   * ```
   *
   * @param table Table to delete from. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL DELETE statement builder
   * @throws IllegalArgumentException if [table] is aliased
   */
  @CheckResult
  infix fun <P> FROM(table: Table<P>) = DeleteFromStage(MutationStatement(MutationNode.delete(table)))

  /**
   * Create raw SQL DELETE statement.
   *
   * Define the tables that the statement changes with `notifies`, so that observed queries of these tables are
   * re-run.
   *
   * Example:
   * ```
   * DELETE
   *     .RAW("DELETE FROM author WHERE id = ?", 1L)
   *     .notifies(AUTHOR)
   *     .execute()
   * ```
   *
   * @param sql SQL DELETE statement
   * @param arguments Values bound to the `?` placeholders of the SQL: `null` or values of type `String`, `Long`,
   *     `Int`, `Short`, `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
   * @return Raw SQL DELETE statement
   * @throws IllegalArgumentException if an argument is a value of another type
   */
  @CheckResult
  fun RAW(sql: String, vararg arguments: Any?) = rawStatement(
    sql = sql,
    arguments = arguments
  )

  /**
   * Create raw SQL DELETE statement.
   *
   * Define the tables that the statement changes with `notifies`, so that observed queries of these tables are
   * re-run.
   *
   * Example:
   * ```
   * (DELETE RAW "DELETE FROM author")
   *     .notifies(AUTHOR)
   *     .execute()
   * ```
   *
   * @param sql SQL DELETE statement
   * @return Raw SQL DELETE statement
   */
  @CheckResult
  infix fun RAW(sql: String) = rawStatement(
    sql = sql,
    arguments = emptyArray()
  )
}

/**
 * Create a new builder for SQL DELETE statement.
 *
 * Example:
 * ```
 * DELETE FROM AUTHOR WHERE (AUTHOR.NAME IS "George")
 * ```
 *
 * @see [SQLite documentation: DELETE](https://www.sqlite.org/lang_delete.html)
 */
val DELETE = Delete

private fun rawStatement(sql: String, arguments: Array<out Any?>) = RawMutationQuery(
  ExpressionNode.Raw(
    sql = sql,
    arguments = arguments.map(::nativeValue),
    dependencies = QueryDependencies.NONE
  )
)

/**
 * Update a not nullable numeric column with the value of a not nullable numeric expression of any
 * numeric type.
 *
 * @param column Column to update. This param must be one of annotation processor
 *               generated column objects that corresponds to column in a database table
 * @param value Expression whose value to assign to [column]
 * @return SQL UPDATE statement builder
 * @throws IllegalArgumentException if [column] is not a column of the updated table, for example a column of an
 *     aliased table
 */
@CheckResult
@JvmName("setRequiredNumber")
fun <P> UpdateTableStage<P>.SET(
  column: Column<P, out Number?, Required, *, *, *, *>,
  value: SqlExpression<Number?, Required, *>
) = assign(
  column = column,
  value = value.node
)

/**
 * Update a not nullable numeric column with the value of a not nullable numeric expression of any
 * numeric type.
 *
 * Example:
 * ```
 * UPDATE TABLE BOOK SET (BOOK.PAGES to BOOK.PAGES + 1)
 * ```
 *
 * @param assignment Column to update and expression whose value to assign to it
 * @return SQL UPDATE statement builder
 * @throws IllegalArgumentException if the assigned column is not a column of the updated table, for example a
 *     column of an aliased table
 */
@CheckResult
@JvmName("setRequiredNumberPair")
infix fun <P> UpdateTableStage<P>.SET(
  assignment: Pair<Column<P, out Number?, Required, *, *, *, *>, SqlExpression<Number?, Required, *>>
) = assign(
  column = assignment.first,
  value = assignment.second.node
)

/**
 * Update a nullable numeric column with the value of a numeric expression of any numeric type.
 *
 * @param column Column to update. This param must be one of annotation processor
 *               generated column objects that corresponds to column in a database table
 * @param value Expression whose value to assign to [column]
 * @return SQL UPDATE statement builder
 * @throws IllegalArgumentException if [column] is not a column of the updated table, for example a column of an
 *     aliased table
 */
@CheckResult
@JvmName("setNullableNumber")
fun <P> UpdateTableStage<P>.SET(
  column: Column<P, out Number?, SqlNullable, *, *, *, *>,
  value: SqlExpression<Number?, *, *>
) = assign(
  column = column,
  value = value.node
)

/**
 * Update a nullable numeric column with the value of a numeric expression of any numeric type.
 *
 * @param assignment Column to update and expression whose value to assign to it
 * @return SQL UPDATE statement builder
 * @throws IllegalArgumentException if the assigned column is not a column of the updated table, for example a
 *     column of an aliased table
 */
@CheckResult
@JvmName("setNullableNumberPair")
infix fun <P> UpdateTableStage<P>.SET(
  assignment: Pair<Column<P, out Number?, SqlNullable, *, *, *, *>, SqlExpression<Number?, *, *>>
) = assign(
  column = assignment.first,
  value = assignment.second.node
)
