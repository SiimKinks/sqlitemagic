package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.QualifiedColumns

internal class ViewDefinition(
  val sql: String,
  val queryDependencies: QueryDependencies,
  val columnNames: Array<String>,
  val blocks: Array<RelatedEntities?>?
) {
  val quotedColumnNames = Array(columnNames.size) { quoteSqlIdentifier(columnNames[it]) }

  private var renderedColumns: QualifiedColumns? = null

  fun renderedColumns(qualifier: String): String {
    val cached = renderedColumns
    if (cached != null && cached.qualifier == qualifier) {
      return cached.sql
    }
    val rendered = QualifiedColumns(
      qualifier = qualifier,
      quotedNames = quotedColumnNames.asList()
    )
    renderedColumns = rendered
    return rendered.sql
  }
}

/** A view property of table or view type, read from the columns named `key.column`. */
@InternalSqliteMagicApi
class ViewBlock(
  internal val key: String,
  internal val target: Table<*>
)
