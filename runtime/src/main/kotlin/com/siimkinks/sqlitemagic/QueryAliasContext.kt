package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.StringArraySet

class QueryAliasContext(
  rootTable: Table<*>,
  joins: List<JoinClause>
) {
  private val reservedIdentifiers = StringArraySet(joins.size + 1)
  private var nextAliasIndex = 0

  init {
    reservedIdentifiers.add(rootTable.nameInQuery)
    for (join in joins) {
      reservedIdentifiers.add(join.tableNameInQuery())
    }
  }

  fun <T> tableForAutomaticJoin(canonicalTable: Table<T>): Table<T> =
    when {
      reservedIdentifiers.add(canonicalTable.name) -> canonicalTable
      else -> canonicalTable.internalAlias(nextAvailableAlias())
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
