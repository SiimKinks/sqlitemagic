package com.siimkinks.sqlitemagic

/**
 * Map each selected value with [transform].
 *
 * The SQL stays the same.
 *
 * Example:
 * ```
 * SELECT
 *     .COLUMN(AUTHOR.ID)
 *     .FROM(AUTHOR)
 *     .mapTo(::AuthorId)
 * ```
 *
 * @param transform Function that creates a result from the selected value
 * @return SQL SELECT statement
 */
fun <R, N : SqlNullability, T, M> SelectQuery<T, ScalarSelection<R, N, T>>.mapTo(transform: (T) -> M) = SelectQuery(
  node.withSelection(node.selection.mapped(transform))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * Example:
 * ```
 * data class AuthorName(val id: Long, val name: String?)
 *
 * SELECT
 *     .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
 *     .FROM(AUTHOR)
 *     .mapTo(::AuthorName)
 * ```
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, M> SelectQuery<Row2<A, B>, RowSelection<Row2<A, B>>>.mapTo(
  transform: (A, B) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read2(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, M> SelectQuery<Row3<A, B, C>, RowSelection<Row3<A, B, C>>>.mapTo(
  transform: (A, B, C) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read3(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, D, M> SelectQuery<Row4<A, B, C, D>, RowSelection<Row4<A, B, C, D>>>.mapTo(
  transform: (A, B, C, D) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read4(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, D, E, M> SelectQuery<Row5<A, B, C, D, E>, RowSelection<Row5<A, B, C, D, E>>>.mapTo(
  transform: (A, B, C, D, E) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read5(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, D, E, F, M> SelectQuery<Row6<A, B, C, D, E, F>, RowSelection<Row6<A, B, C, D, E, F>>>.mapTo(
  transform: (A, B, C, D, E, F) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read6(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, D, E, F, G, M> SelectQuery<Row7<A, B, C, D, E, F, G>, RowSelection<Row7<A, B, C, D, E, F, G>>>.mapTo(
  transform: (A, B, C, D, E, F, G) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read7(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, D, E, F, G, H, M> SelectQuery<Row8<A, B, C, D, E, F, G, H>, RowSelection<Row8<A, B, C, D, E, F, G, H>>>.mapTo(
  transform: (A, B, C, D, E, F, G, H) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read8(transform)))
)

/**
 * Map each row of selected values with [transform].
 *
 * The SQL stays the same.
 *
 * @param transform Function that creates a result from the selected values
 * @return SQL SELECT statement
 */
fun <A, B, C, D, E, F, G, H, I, M> SelectQuery<Row9<A, B, C, D, E, F, G, H, I>, RowSelection<Row9<A, B, C, D, E, F, G, H, I>>>.mapTo(
  transform: (A, B, C, D, E, F, G, H, I) -> M
) = SelectQuery(
  node.withSelection(node.selection.withReader(node.selection.columnReads.read9(transform)))
)

/**
 * Map each row with [transform].
 *
 * The row passed to [transform] reads the current cursor row and is valid only during the call, so do not keep a
 * reference to it. The SQL stays the same.
 *
 * Example:
 * ```
 * SELECT
 *     .COLUMNS(BOOK.ID, AUTHOR.all())
 *     .FROM(BOOK)
 *     .mapTo { row -> row[BOOK.ID] }
 * ```
 *
 * @param transform Function that creates a result from the row
 * @return SQL SELECT statement
 */
@JvmName("mapSelectedRows")
fun <M> SelectQuery<SelectedRow, RowSelection<SelectedRow>>.mapTo(transform: (SelectedRow) -> M) = SelectQuery(
  node.withSelection(node.selection.withReader(SelectedOutputs(node.selection.outputs).liveRows(transform)))
)

/**
 * Read each row into a partial model of [table].
 *
 * Selected columns must be columns of [table] or of the tables its relationships reach, and the columns of
 * all not nullable model properties must be selected. Not selected nullable properties are read as `null`.
 *
 * Example:
 * ```
 * SELECT
 *     .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
 *     .FROM(AUTHOR)
 *     .mapTo(AUTHOR)
 * ```
 *
 * @param table Table of the model
 * @return SQL SELECT statement
 * @throws IllegalArgumentException when the query is compiled if [table] is not a source of the query, a selected
 *     value is not a column of [table] or of a table its relationships reach, or a required column is not selected
 */
@JvmName("mapRowsToTable")
fun <T, M> SelectQuery<T, RowSelection<T>>.mapTo(table: Table<M>) = SelectQuery(
  node.withSelection(
    PartialEntitySelection(
      outputs = node.selection.outputs,
      table = table
    )
  )
)

/**
 * Read each row into a partial model of [table].
 *
 * The selected column must be a column of [table] or of a table its relationships reach, and the columns of
 * all not nullable model properties must be selected. Not selected nullable properties are read as `null`.
 *
 * @param table Table of the model
 * @return SQL SELECT statement
 * @throws IllegalArgumentException when the query is compiled if [table] is not a source of the query, the selected
 *     value is not a column of [table] or of a table its relationships reach, or a required column is not selected
 */
@JvmName("mapScalarToTable")
fun <R, N : SqlNullability, T, M> SelectQuery<T, ScalarSelection<R, N, T>>.mapTo(table: Table<M>) = SelectQuery(
  node.withSelection(
    PartialEntitySelection(
      outputs = listOf(node.selection.expression),
      table = table
    )
  )
)
