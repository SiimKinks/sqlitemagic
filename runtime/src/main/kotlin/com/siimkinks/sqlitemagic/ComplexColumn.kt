package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.Utils.ValueParser

/**
 * A relationship column whose ID is stored with a non-numeric SQLite affinity.
 *
 * @param ID Referenced model ID type
 * @param R Return type (when this column is queried)
 * @param ET Equivalent type
 * @param P Parent table type
 * @param N Column nullability
 */
open class ComplexColumn<ID, R, ET, P, N>(
  table: Table<P>,
  name: String,
  allFromTable: Boolean = false,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?,
  valueAdapter: ColumnValueAdapter<ID>? = null
) : Column<ID, R, ET, P, N>(
  table = table,
  name = name,
  allFromTable = allFromTable,
  valueParser = valueParser,
  nullable = nullable,
  alias = alias,
  valueAdapter = valueAdapter
)
