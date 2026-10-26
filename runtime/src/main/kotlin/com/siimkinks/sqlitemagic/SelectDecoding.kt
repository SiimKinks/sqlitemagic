package com.siimkinks.sqlitemagic

/**
 * Define how the selected values are read.
 *
 * The SQL stays the same.
 *
 * Example:
 * ```
 * SELECT
 *     .COLUMN(COUNT())
 *     .FROM(BOOK)
 *     .readAs(SqlType.INT)
 * ```
 *
 * @param descriptor Type that reads the selected values
 * @return SQL SELECT statement
 */
@JvmName("requiredSelectQueryReadAs")
fun <S, T, V> SelectQuery<T, ScalarSelection<S, Required, T>>.readAs(
  descriptor: ReadType<*, *, V>
) = SelectQuery(
  node.withSelection(node.selection.withReader(descriptor.selectRead))
)

/**
 * Define how the selected values are read.
 *
 * The SQL stays the same and SQL NULL values are read as `null`.
 *
 * Example:
 * ```
 * SELECT
 *     .COLUMN(BOOK.PAGES + 1)
 *     .FROM(BOOK)
 *     .readAs(SqlType.LONG)
 * ```
 *
 * @param descriptor Type that reads the selected values
 * @return SQL SELECT statement
 */
@JvmName("nullableSelectQueryReadAs")
fun <S, T, R> SelectQuery<T, ScalarSelection<S, SqlNullable, T>>.readAs(
  descriptor: ReadType<R, *, *>
) = SelectQuery(
  node.withSelection(node.selection.withReader(descriptor.nullable.selectRead))
)
