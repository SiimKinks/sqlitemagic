@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.InsertHead
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
import com.siimkinks.sqlitemagic.internal.sql.withDoNothing
import com.siimkinks.sqlitemagic.internal.sql.withDoUpdate
import com.siimkinks.sqlitemagic.internal.sql.withUpsertAssignment
import com.siimkinks.sqlitemagic.internal.sql.withUpsertWhere

/**
 * SQL INSERT statement that can take an upsert clause, ON CONFLICT.
 *
 * UPSERT needs SQLite 3.24.0, which the framework driver has from API 30. Several ON CONFLICT clauses, and DO UPDATE
 * in a clause without a conflict target, need SQLite 3.35.0, which the framework driver has from API 34. The bundled
 * driver supports both, and older SQLite rejects the statement with a syntax error.
 *
 * @param P Inserted table model type
 * @see [SQLite documentation: UPSERT](https://www.sqlite.org/lang_upsert.html)
 */
sealed interface OnConflictClause<P> {
  /**
   * Add an upsert clause for conflicts on the uniqueness constraint of a column, such as a unique column or the
   * primary key.
   *
   * SQLite checks that a PRIMARY KEY, a UNIQUE constraint or a unique index has exactly this column. Another
   * ON CONFLICT clause can follow this one, and SQLite tries the clauses in order. When the statement inserts the rows
   * of a query that ends in its FROM clause, the query gets `WHERE true`, so that SQLite does not read ON CONFLICT as
   * a join constraint.
   *
   * Example:
   * ```
   * (INSERT INTO BOOK COLUMNS BOOK.ISBN VALUES "9780441013593" ON_CONFLICT BOOK.ISBN).DO_NOTHING()
   * ```
   *
   * @param column Column of the uniqueness constraint
   * @return Builder for the action of the upsert clause
   * @throws IllegalArgumentException if [column] is a column of an aliased table
   */
  @CheckResult
  infix fun ON_CONFLICT(column: Column<P, *, *, *, *, *, *>): UpsertStage<P> = UpsertStage(
    node = upsertNode(),
    target = arrayOf(column)
  )

  /**
   * Add an upsert clause for conflicts on the uniqueness constraint of several columns, such as a composite unique
   * constraint.
   *
   * SQLite checks that a PRIMARY KEY, a UNIQUE constraint or a unique index has exactly these columns. Another
   * ON CONFLICT clause can follow this one, and SQLite tries the clauses in order. When the statement inserts the rows
   * of a query that ends in its FROM clause, the query gets `WHERE true`, so that SQLite does not read ON CONFLICT as
   * a join constraint.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(BOOK)
   *     .COLUMNS(BOOK.AUTHOR, BOOK.TITLE)
   *     .VALUES(authorId, "Dune")
   *     .ON_CONFLICT(BOOK.AUTHOR, BOOK.TITLE)
   *     .DO_NOTHING()
   * ```
   *
   * @param first The first column of the uniqueness constraint
   * @param rest The other columns of the uniqueness constraint
   * @return Builder for the action of the upsert clause
   * @throws IllegalArgumentException if a column is a column of an aliased table
   */
  @CheckResult
  fun ON_CONFLICT(first: Column<P, *, *, *, *, *, *>, vararg rest: Column<P, *, *, *, *, *, *>): UpsertStage<P> =
      UpsertStage(
        node = upsertNode(),
        target = targetColumns(
          first = first,
          rest = rest
        )
      )

  /**
   * Add an upsert clause for conflicts on any uniqueness constraint.
   *
   * This clause is the last one of the statement. Its DO UPDATE needs SQLite 3.35.0, which the framework driver has
   * from API 34; its DO NOTHING needs 3.24.0. When the statement inserts the rows of a query that ends in its FROM
   * clause, the query gets `WHERE true`, so that SQLite does not read ON CONFLICT as a join constraint.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(BOOK)
   *     .COLUMNS(BOOK.ISBN, BOOK.TITLE)
   *     .VALUES("9780441013593", "Dune")
   *     .ON_CONFLICT()
   *     .DO_NOTHING()
   * ```
   *
   * @return Builder for the action of the upsert clause
   */
  @CheckResult
  fun ON_CONFLICT(): LastUpsertStage<P> = LastUpsertStage(upsertNode())
}

/**
 * SQL INSERT statement of one row that can take an upsert clause, ON CONFLICT, and keeps reading the row ID of the
 * inserted row through DO NOTHING.
 *
 * @param P Inserted table model type
 */
sealed interface RowIdOnConflictClause<P> : OnConflictClause<P> {
  @CheckResult
  override infix fun ON_CONFLICT(column: Column<P, *, *, *, *, *, *>): RowIdUpsertStage<P> = RowIdUpsertStage(
    node = upsertNode(),
    target = arrayOf(column)
  )

  @CheckResult
  override fun ON_CONFLICT(
    first: Column<P, *, *, *, *, *, *>,
    vararg rest: Column<P, *, *, *, *, *, *>
  ): RowIdUpsertStage<P> = RowIdUpsertStage(
    node = upsertNode(),
    target = targetColumns(
      first = first,
      rest = rest
    )
  )

  @CheckResult
  override fun ON_CONFLICT(): RowIdLastUpsertStage<P> = RowIdLastUpsertStage(upsertNode())
}

/**
 * Builder for the action of an upsert clause with a conflict target.
 *
 * @param P Inserted table model type
 */
open class UpsertStage<P> internal constructor(
  internal val node: MutationNode<InsertHead>,
  target: Array<out Column<*, *, *, *, *, *, *>>
) {
  internal val target = node.head.conflictTarget(target)

  /**
   * Skip the rows that conflict.
   *
   * @return SQL INSERT statement, which can take another upsert clause
   */
  @CheckResult
  open fun DO_NOTHING(): UpsertDoNothingStage<P> = UpsertDoNothingStage(node.withDoNothing(target))

  /**
   * Update the existing row instead of inserting a row that conflicts.
   *
   * Follow it with SET. Assignments and WHERE read the existing row through the columns of the inserted table and
   * the proposed row through `EXCLUDED`, as in `SET(BOOK.TITLE, BOOK.TITLE.EXCLUDED)`. The update fails on any
   * constraint violation, whatever conflict algorithm the INSERT statement has. A statement with DO UPDATE counts
   * the updated rows with the inserted rows and cannot read the row ID of the inserted row.
   *
   * @return Builder for the SET clause of DO UPDATE
   */
  @Suppress("PropertyName")
  val DO_UPDATE get() = UpsertDoUpdateStage<P>(node.withDoUpdate(target))
}

/**
 * Builder for the action of an upsert clause with a conflict target in SQL INSERT statement of one row.
 *
 * @param P Inserted table model type
 */
class RowIdUpsertStage<P> internal constructor(
  node: MutationNode<InsertHead>,
  target: Array<out Column<*, *, *, *, *, *, *>>
) : UpsertStage<P>(
  node = node,
  target = target
) {
  @CheckResult
  override fun DO_NOTHING() = RowIdUpsertDoNothingStage<P>(node.withDoNothing(target))
}

/**
 * Builder for the action of the last upsert clause, which has no conflict target.
 *
 * @param P Inserted table model type
 */
open class LastUpsertStage<P> internal constructor(
  internal val node: MutationNode<InsertHead>
) {
  /**
   * Skip the rows that conflict.
   *
   * @return SQL INSERT statement
   */
  @CheckResult
  open fun DO_NOTHING(): MutationQuery = MutationStatement(node.withDoNothing(NO_TARGET))

  /**
   * Update the existing row instead of inserting a row that conflicts.
   *
   * Follow it with SET. Assignments and WHERE read the existing row through the columns of the inserted table and
   * the proposed row through `EXCLUDED`, as in `SET(BOOK.TITLE, BOOK.TITLE.EXCLUDED)`. The update fails on any
   * constraint violation, whatever conflict algorithm the INSERT statement has. A statement with DO UPDATE counts
   * the updated rows with the inserted rows and cannot read the row ID of the inserted row. DO UPDATE without a
   * conflict target needs SQLite 3.35.0, which the framework driver has from API 34.
   *
   * @return Builder for the SET clause of DO UPDATE
   */
  @Suppress("PropertyName")
  val DO_UPDATE get() = LastUpsertDoUpdateStage<P>(node.withDoUpdate(NO_TARGET))
}

/**
 * Builder for the action of the last upsert clause, which has no conflict target, in SQL INSERT statement of one row.
 *
 * @param P Inserted table model type
 */
class RowIdLastUpsertStage<P> internal constructor(
  node: MutationNode<InsertHead>
) : LastUpsertStage<P>(node) {
  @CheckResult
  override fun DO_NOTHING() = RowIdLastUpsertDoNothingStage(node.withDoNothing(NO_TARGET))
}

/**
 * SQL INSERT statement whose last upsert clause skips the rows that conflict, which can take another upsert clause.
 *
 * @param P Inserted table model type
 */
open class UpsertDoNothingStage<P> internal constructor(
  internal val node: MutationNode<InsertHead>
) : MutationQuery by MutationStatement(node), OnConflictClause<P>

/**
 * SQL INSERT statement of one row whose last upsert clause skips the row if it conflicts, which can take another
 * upsert clause or read the row ID of the inserted row.
 *
 * @param P Inserted table model type
 */
class RowIdUpsertDoNothingStage<P> internal constructor(
  node: MutationNode<InsertHead>
) : UpsertDoNothingStage<P>(node), RowIdOnConflictClause<P> {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that DO NOTHING skips.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * SQL INSERT statement of one row whose last upsert clause skips the row if it conflicts, which can read the row ID
 * of the inserted row.
 */
class RowIdLastUpsertDoNothingStage internal constructor(
  private val node: MutationNode<InsertHead>
) : MutationQuery by MutationStatement(node) {
  /**
   * Read the row ID of the inserted row instead of the number of inserted rows.
   *
   * The statement reads `null` when it inserts no row, such as a row that DO NOTHING skips.
   *
   * @return SQL INSERT statement that reads the row ID of the inserted row
   * @throws IllegalArgumentException if the inserted table is a `WITHOUT ROWID` table
   */
  @CheckResult
  fun readRowId() = node.rowIdInsert()
}

/**
 * Builder for the SET clause of DO UPDATE in an upsert clause with a conflict target.
 *
 * @param P Inserted table model type
 */
open class UpsertDoUpdateStage<P> internal constructor(
  internal val node: MutationNode<InsertHead>
) : AssignmentStage<P, UpsertSetStage<P>>() {
  override fun assign(column: Column<P, *, *, *, *, *, *>, value: ExpressionNode) = UpsertSetStage<P>(
    node.withUpsertAssignment(
      column = column,
      value = value
    )
  )
}

/**
 * SQL INSERT statement whose last upsert clause updates the existing row, which can take more assignments, a WHERE
 * clause or another upsert clause.
 *
 * Without WHERE clause the existing row of every conflict is updated.
 *
 * @param P Inserted table model type
 */
class UpsertSetStage<P> internal constructor(
  node: MutationNode<InsertHead>
) : UpsertDoUpdateStage<P>(node), MutationQuery by MutationStatement(node), OnConflictClause<P> {
  /**
   * Define the WHERE clause of DO UPDATE.
   *
   * A conflicting row whose existing row does not match is neither inserted nor updated.
   *
   * Example:
   * ```
   * INSERT
   *     .INTO(BOOK)
   *     .COLUMNS(BOOK.ISBN, BOOK.PAGES)
   *     .VALUES("9780441013593", 412)
   *     .ON_CONFLICT(BOOK.ISBN)
   *     .DO_UPDATE
   *     .SET(BOOK.PAGES, BOOK.PAGES.EXCLUDED)
   *     .WHERE(BOOK.PAGES.EXCLUDED GREATER_THAN BOOK.PAGES)
   * ```
   *
   * @param predicate WHERE clause expression
   * @return SQL INSERT statement, which can take another upsert clause
   */
  @CheckResult
  infix fun WHERE(predicate: SqlExpression<Boolean, *, *>) = UpsertWhereStage<P>(node.withUpsertWhere(predicate))
}

/**
 * SQL INSERT statement whose last upsert clause updates the existing row when it matches a condition, which can take
 * another upsert clause.
 *
 * @param P Inserted table model type
 */
class UpsertWhereStage<P> internal constructor(
  internal val node: MutationNode<InsertHead>
) : MutationQuery by MutationStatement(node), OnConflictClause<P>

/**
 * Builder for the SET clause of DO UPDATE in the last upsert clause, which has no conflict target.
 *
 * @param P Inserted table model type
 */
open class LastUpsertDoUpdateStage<P> internal constructor(
  internal val node: MutationNode<InsertHead>
) : AssignmentStage<P, LastUpsertSetStage<P>>() {
  override fun assign(column: Column<P, *, *, *, *, *, *>, value: ExpressionNode) = LastUpsertSetStage<P>(
    node.withUpsertAssignment(
      column = column,
      value = value
    )
  )
}

/**
 * SQL INSERT statement whose last upsert clause, which has no conflict target, updates the existing row, which can
 * take more assignments or a WHERE clause.
 *
 * Without WHERE clause the existing row of every conflict is updated.
 *
 * @param P Inserted table model type
 */
class LastUpsertSetStage<P> internal constructor(
  node: MutationNode<InsertHead>
) : LastUpsertDoUpdateStage<P>(node), MutationQuery by MutationStatement(node) {
  /**
   * Define the WHERE clause of DO UPDATE.
   *
   * A conflicting row whose existing row does not match is neither inserted nor updated.
   *
   * @param predicate WHERE clause expression
   * @return SQL INSERT statement
   */
  @CheckResult
  infix fun WHERE(predicate: SqlExpression<Boolean, *, *>): MutationQuery =
      MutationStatement(node.withUpsertWhere(predicate))
}

/**
 * The value that the INSERT statement proposes for this column, `excluded.column` in SQL.
 *
 * Use it in DO UPDATE of an upsert clause, as in `SET(BOOK.PAGES, BOOK.PAGES.EXCLUDED)`. It is compared with and
 * converts values as the column does, including the column's transformer, as in
 * `BOOK.PAGES.EXCLUDED GREATER_THAN 400`. A statement that reads `EXCLUDED` of a column of another table or of an
 * aliased table fails with [IllegalArgumentException] when it is compiled. Outside DO UPDATE SQLite rejects it with
 * "no such column".
 */
val <R, N : SqlNullability, T, W> Column<*, R, N, T, W, *, *>.EXCLUDED: EncodableSqlExpression<W, R, N, T>
  get() = EncodableSqlExpression(
    source = this,
    node = ExpressionNode.Excluded(fieldNode),
    outputAlias = null
  )

private val NO_TARGET = emptyArray<Column<*, *, *, *, *, *, *>>()

private fun targetColumns(first: Column<*, *, *, *, *, *, *>, rest: Array<out Column<*, *, *, *, *, *, *>>) =
    Array(rest.size + 1) { index ->
      when (index) {
        0 -> first
        else -> rest[index - 1]
      }
    }

internal fun OnConflictClause<*>.upsertNode() = when (this) {
  is InsertValues1Stage<*, *, *> -> node
  is InsertValues2Stage<*, *, *, *, *> -> node
  is InsertValues3Stage<*, *, *, *, *, *, *> -> node
  is InsertValues4Stage<*, *, *, *, *, *, *, *, *> -> node
  is InsertValues5Stage<*, *, *, *, *, *, *, *, *, *, *> -> node
  is InsertValues6Stage<*, *, *, *, *, *, *, *, *, *, *, *, *> -> node
  is InsertValues7Stage<*, *, *, *, *, *, *, *, *, *, *, *, *, *, *> -> node
  is InsertValues8Stage<*, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *> -> node
  is InsertValues9Stage<*, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *, *> -> node
  is InsertValuesStage<*> -> node
  is InsertSelectStage<*> -> node
  is UpsertDoNothingStage<*> -> node
  is UpsertSetStage<*> -> node
  is UpsertWhereStage<*> -> node
}
