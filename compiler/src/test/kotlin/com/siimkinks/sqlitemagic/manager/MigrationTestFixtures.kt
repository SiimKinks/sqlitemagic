package com.siimkinks.sqlitemagic.manager

internal fun migrationTable(
  name: String,
  columns: ArrayList<ColumnStructure>
) = TableStructure(
  name = name,
  schema = "CREATE TABLE IF NOT EXISTS $name (${columns.joinToString(transform = ColumnStructure::schema)})",
  columns = columns
)

internal fun migrationColumn(
  name: String,
  schema: String,
  onDeleteCascade: Boolean = false
) = ColumnStructure(
  name = name,
  onDeleteCascade = onDeleteCascade,
  schema = schema
)
