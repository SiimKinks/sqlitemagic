package com.siimkinks.sqlitemagic

/**
 * A unique column used in queries and conditions.
 *
 * @param T Exact type
 * @param R Return type (when this column is queried)
 * @param ET Equivalent type
 * @param P Parent table type
 * @param N Column nullability
 */
class UniqueColumn<T, R, ET, P, N>(
  table: Table<P>,
  name: String,
  allFromTable: Boolean = false,
  valueParser: Utils.ValueParser<*>,
  nullable: Boolean,
  alias: String?
) : Column<T, R, ET, P, N>(
  table = table,
  name = name,
  allFromTable = allFromTable,
  valueParser = valueParser,
  nullable = nullable,
  alias = alias
), Unique<N> {
  override fun `as`(alias: String) = UniqueColumn<T, R, ET, P, N>(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )

  override fun <NewTableType> inTable(
    table: Table<NewTableType>
  ) = UniqueColumn<T, R, ET, NewTableType, N>(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )
}
