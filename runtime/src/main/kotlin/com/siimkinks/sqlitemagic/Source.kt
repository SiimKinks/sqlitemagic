package com.siimkinks.sqlitemagic

/**
 * Source of rows in FROM and JOIN clauses: a table, a view, a full-text index, a subquery or a common table
 * expression.
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
   * @param selected Column or expression selected by the query. It must be the same instance that is selected. A not
   *     nullable column of selected `all().nullable()` columns is referenced with its `nullable()`
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
   * @param selected Column or expression selected by the query. It must be the same instance that is selected. A not
   *     nullable column of selected `all().nullable()` columns is referenced with its `nullable()`
   * @return Column of this source
   * @throws IllegalArgumentException if the query does not select [selected]
   */
  operator fun <R, N : SqlNullability, V> get(selected: SqlExpression<R, N, V>) = occurrence.exportValue(selected)

  /**
   * Reference an entity selected by the query of this source.
   *
   * The entity is read from the columns that the query exports for it, with the related objects that the query
   * reads. Select it with `nullable()` when an outer join can leave this source NULL.
   *
   * Example:
   * ```
   * SELECT.COLUMNS(totals[AUTHOR], totals[bookCount]) FROM totals
   * ```
   *
   * @param entity Table or derived entity selected by the query. It must be the same instance that is selected
   * @return Entity of this source
   * @throws IllegalArgumentException if the query does not select [entity], or selects it with `nullable()`
   */
  operator fun <T> get(entity: EntityValue<T>) = occurrence.exportEntity(entity)

  /**
   * Reference an entity that the query of this source selects with `nullable()`.
   *
   * Example:
   * ```
   * SELECT.COLUMNS(shelves[BOOK.TITLE], shelves[AUTHOR.nullable()]) FROM shelves
   * ```
   *
   * @param entity Nullable entity selected by the query. It must be the same instance that is selected
   * @return Nullable entity of this source
   * @throws IllegalArgumentException if the query does not select [entity]
   */
  operator fun <T> get(entity: NullableEntity<T>) = occurrence.exportNullableEntity(entity)

  internal abstract fun cte(): Cte<T>
}

internal fun Source.occurrence() = when (this) {
  is Table<*> -> this
  is FullTextTable -> table
  is QueryOutputSource<*> -> occurrence
}
