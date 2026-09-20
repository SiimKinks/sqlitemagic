package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.StringArraySet

class QueryAliasContext(from: Select.From<*, *, *, *>) {
  private val joins = from.joins
  private val reservedIdentifiers = StringArraySet(joins.size + 1)
  private var nextAliasIndex = 0

  init {
    reservedIdentifiers.add(from.table.nameInQuery)
    for (join in joins) {
      reservedIdentifiers.add(join.tableNameInQuery())
    }
  }

  fun <T> tableForAutomaticJoin(canonicalTable: Table<T>): Table<T> =
    when {
      reservedIdentifiers.add(canonicalTable.name) -> canonicalTable
      else -> canonicalTable.internalAlias(nextAvailableAlias())
    }

  fun findJoin(
    table: Table<*>,
    joinedOnColumn: Column<*, *, *, *, *>
  ): JoinClause? = JoinClause
    .indexOf(table, joins, joinedOnColumn)
    .takeIf { it != -1 }
    ?.let(joins::get)

  fun addJoin(join: JoinClause) {
    joins += join
    reservedIdentifiers.add(join.tableNameInQuery())
  }

  private fun nextAvailableAlias(): String {
    while (true) {
      val alias = "sm_${nextAliasIndex++}"
      if (reservedIdentifiers.add(alias)) {
        return alias
      }
    }
  }
}
