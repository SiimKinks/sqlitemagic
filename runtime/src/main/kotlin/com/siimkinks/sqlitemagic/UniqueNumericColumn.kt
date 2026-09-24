package com.siimkinks.sqlitemagic

/**
 * A unique numeric column used in queries and conditions.
 *
 * @param T Exact type
 * @param R Return type (when this column is queried)
 * @param ET Equivalent type
 * @param P Parent table type
 * @param N Column nullability
 */
class UniqueNumericColumn<T, R, ET, P, N>(
  table: Table<P>,
  name: String,
  allFromTable: Boolean = false,
  valueParser: Utils.ValueParser<*>,
  nullable: Boolean,
  alias: String?
) : NumericColumn<T, R, ET, P, N>(
  table = table,
  name = name,
  allFromTable = allFromTable,
  valueParser = valueParser,
  nullable = nullable,
  alias = alias
), Unique<N> {
  override fun `as`(alias: String) = UniqueNumericColumn<T, R, ET, P, N>(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )

  override fun <NewTableType> inTable(
    table: Table<NewTableType>
  ) = UniqueNumericColumn<T, R, ET, NewTableType, N>(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )
}
