package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier

internal fun IndexElement.createSql() = buildString {
  append("CREATE ")
  if (isUnique) {
    append("UNIQUE ")
  }
  append("INDEX IF NOT EXISTS ")
  append(schema.qualifier)
  append('.')
  append(quoteSqlIdentifier(name))
  append(" ON ")
  append(quoteSqlIdentifier(tableName))
  append(" (")
  append(columns.joinToString(separator = ", ", transform = { column ->
    quoteSqlIdentifier(column.columnName)
  }))
  append(')')
}

internal fun dropMainSchemaIndexSql(indexName: String) =
  "DROP INDEX IF EXISTS main.${quoteSqlIdentifier(indexName)}"
