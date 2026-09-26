package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.Utils.ValueParser

internal class RawColumn<T, R, ET, P, N>(
  table: Table<P>,
  name: String,
  allFromTable: Boolean,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?,
  valueAdapter: ColumnValueAdapter<T>? = null
) : Column<T, R, ET, P, N>(
  table = table,
  name = name,
  allFromTable = allFromTable,
  valueParser = valueParser,
  nullable = nullable,
  alias = alias,
  valueAdapter = valueAdapter
) {
  override fun addDependencies(dependencies: QueryDependencies.Builder) {
    dependencies.markDirectSourcesIncomplete()
  }

  override fun `as`(alias: String): RawColumn<T, R, ET, P, N> = RawColumn(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    valueAdapter = valueAdapter
  )

  override fun <NewTableType> inTable(table: Table<NewTableType>) = RawColumn<T, R, ET, NewTableType, N>(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    valueAdapter = valueAdapter
  )
}
