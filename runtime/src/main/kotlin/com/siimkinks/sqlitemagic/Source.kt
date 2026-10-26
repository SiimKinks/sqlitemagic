package com.siimkinks.sqlitemagic

/**
 * Source of rows in FROM and JOIN clauses: a table, a view, a subquery or a common table expression.
 */
sealed interface Source

/**
 * Source whose rows are the results of a query: the query itself, an aliased subquery or a common table expression.
 *
 * Reference the selected columns and expressions with `source[selected]`.
 *
 * @param T Row type
 */
sealed class QueryOutputSource<T> : Source {
  internal abstract val occurrence: QueryOutputTable<T>

  /**
   * Reference a column or expression selected by the query of this source.
   *
   * Example:
   * ```
   * SELECT COLUMN published[BOOK.TITLE] FROM published
   * ```
   *
   * @param selected Column or expression selected by the query. It must be the same instance that is selected
   * @return Column of this source
   * @throws IllegalArgumentException if the query does not select [selected]
   */
  operator fun <W, R, N : SqlNullability, V> get(
    selected: EncodableSqlExpression<W, R, N, V>
  ) = occurrence.export(selected)

  /**
   * Reference a column or expression selected by the query of this source.
   *
   * Example:
   * ```
   * SELECT COLUMN published[BOOK.TITLE] FROM published
   * ```
   *
   * @param selected Column or expression selected by the query. It must be the same instance that is selected
   * @return Column of this source
   * @throws IllegalArgumentException if the query does not select [selected]
   */
  operator fun <R, N : SqlNullability, V> get(selected: SqlExpression<R, N, V>) = occurrence.exportValue(selected)

  internal abstract fun cte(): Cte<T>
}

internal fun Source.occurrence() = when (this) {
  is Table<*> -> this
  is QueryOutputSource<*> -> occurrence
}
