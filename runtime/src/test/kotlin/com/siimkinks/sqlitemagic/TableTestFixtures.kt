package com.siimkinks.sqlitemagic

internal fun <T> testTable(
  name: String,
  alias: String? = null,
  temporary: Boolean = false,
  nrOfColumns: Int = 1,
  mapper: TableMapper<T> = { _, _, _ -> error("Test table does not provide row mapping") },
  addDeepQueryParts: TableQueryGraphContributor = { _, _, _ -> },
  addShallowQueryParts: TableQueryGraphContributor = { _, _, _ -> }
): Table<T> = object : Table<T>(
  name = name,
  alias = alias,
  temporary = temporary,
  nrOfColumns = nrOfColumns,
  mapper = mapper,
  addDeepQueryParts = addDeepQueryParts,
  addShallowQueryParts = addShallowQueryParts
) {}
