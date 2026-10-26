package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier

internal class ViewDefinition(
  val sql: String,
  val queryDependencies: QueryDependencies,
  val columnNames: Array<String>,
  val blocks: Array<RelatedEntities?>?
) {
  val quotedColumnNames = Array(columnNames.size) { quoteSqlIdentifier(columnNames[it]) }
}

/** A view property of table or view type, read from the columns named `key.column`. */
@InternalSqliteMagicApi
class ViewBlock(
  internal val key: String,
  internal val target: Table<*>
)
