@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
import com.siimkinks.sqlitemagic.internal.sql.nativeValue

/**
 * Builder for SQL INSERT statement.
 *
 * Start statements with [INSERT].
 */
object Insert {
  /**
   * Define a table to insert into.
   *
   * Follow it with `COLUMNS` and `VALUES`, or with `DEFAULT_VALUES`.
   *
   * Example:
   * ```
   * INSERT INTO AUTHOR COLUMNS AUTHOR.NAME VALUES "Ada"
   * ```
   *
   * @param table Table to insert into. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL INSERT statement builder
   * @throws IllegalArgumentException if [table] is aliased
   */
  @CheckResult
  infix fun <P> INTO(table: Table<P>) = insertInto(
    table = table,
    conflictAlgorithm = CONFLICT_NONE
  )

  /**
   * Define a conflict algorithm for the INSERT statement.
   *
   * Example:
   * ```
   * INSERT OR CONFLICT_IGNORE INTO AUTHOR COLUMNS AUTHOR.NAME VALUES "Ada"
   * ```
   *
   * @param conflictAlgorithm Conflict resolution algorithm to use
   * @return SQL INSERT statement builder
   */
  @CheckResult
  infix fun OR(@ConflictAlgorithm conflictAlgorithm: Int) = InsertConflictStage(conflictAlgorithm)

  /**
   * Create raw SQL INSERT statement.
   *
   * Define the tables that the statement changes with `notifies`, so that observed queries of these tables are
   * re-run.
   *
   * Example:
   * ```
   * INSERT
   *     .RAW("INSERT INTO author (name) VALUES (?)", "Ada")
   *     .notifies(AUTHOR)
   *     .execute()
   * ```
   *
   * @param sql SQL INSERT statement
   * @param arguments Values bound to the `?` placeholders of the SQL: `null` or values of type `String`, `Long`,
   *     `Int`, `Short`, `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
   * @return Raw SQL INSERT statement
   * @throws IllegalArgumentException if an argument is a value of another type
   */
  @CheckResult
  fun RAW(sql: String, vararg arguments: Any?) = rawStatement(
    sql = sql,
    arguments = arguments
  )

  /**
   * Create raw SQL INSERT statement.
   *
   * Define the tables that the statement changes with `notifies`, so that observed queries of these tables are
   * re-run.
   *
   * Example:
   * ```
   * (INSERT RAW "INSERT INTO author DEFAULT VALUES")
   *     .notifies(AUTHOR)
   *     .execute()
   * ```
   *
   * @param sql SQL INSERT statement
   * @return Raw SQL INSERT statement
   */
  @CheckResult
  infix fun RAW(sql: String) = rawStatement(
    sql = sql,
    arguments = emptyArray()
  )
}

/**
 * Create a new builder for SQL INSERT statement.
 *
 * The statement inserts rows of the listed columns, and the other columns take their default values. The count of a
 * statement is SQLite's `changes()`: rows that a conflict algorithm ignores are not counted.
 *
 * Example:
 * ```
 * INSERT INTO AUTHOR COLUMNS AUTHOR.NAME VALUES "Ada"
 *
 * INSERT
 *     .INTO(BOOK)
 *     .COLUMNS(BOOK.TITLE, BOOK.PAGES)
 *     .VALUES("Dune", 412)
 *     .VALUES("Emma", 474)
 *     .execute()
 * ```
 *
 * @see [SQLite documentation: INSERT](https://www.sqlite.org/lang_insert.html)
 */
val INSERT = Insert

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
