package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.Utils.addTableAlias
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import java.util.LinkedList

/** Runtime-owned state and operations for generated query-graph traversal. */
class QueryGraphScope internal constructor(
  private val from: Select.From<*, *, *, *>,
  private val selectFromTables: StringArraySet?,
  private val tableGraphNodeNames: SimpleArrayMap<String, String>?,
  private val queryDeep: Boolean,
  val select1: Boolean
) {
  private val joins = from.joins
  private val reservedIdentifiers = StringArraySet(joins.size + 1)
  private val systemRenamedTables = selectFromTables?.let {
    SimpleArrayMap<String, LinkedList<String>>()
  }
  private var nextAliasIndex = 0

  init {
    reservedIdentifiers.add(from.table.nameInQuery)
    for (join in joins) {
      reservedIdentifiers.add(join.tableNameInQuery)
    }
  }

  /** Contributes one table occurrence and recursively visits its generated query graph. */
  fun visit(
    table: Table<*>,
    tableAlias: Table<*>,
    nodeName: String
  ) {
    recordGraphNode(
      nodeName = nodeName,
      table = tableAlias
    )
    with(if (queryDeep) table.addDeepQueryParts else table.addShallowQueryParts) {
      contribute(
        table = table,
        tableAlias = tableAlias,
        nodeName = nodeName
      )
    }
  }

  /** Returns whether a table participates in the current selection. */
  fun includes(tableName: String) = selectFromTables == null || tableName in selectFromTables

  /** Returns a collision-free table instance for a generated automatic join. */
  fun <T> tableForAutomaticJoin(canonicalTable: Table<T>) = when {
    reservedIdentifiers.add(canonicalTable.name) -> canonicalTable
    else -> canonicalTable.internalAlias(nextAvailableAlias())
  }

  /** Resolves an existing join while generated relationship traversal is building a query. */
  fun findJoin(
    table: Table<*>,
    joinedOnColumn: Column<*, *, *, *, *>
  ): Table<*>? = JoinClause
    .indexOf(table, joins, joinedOnColumn)
    .takeIf { it != -1 }
    ?.let { joins[it].table }

  /** Rebinds a generated relationship column without exposing the runtime copy state. */
  fun <T, R, ET, P, N> rebindColumn(
    newTable: Table<P>,
    column: Column<T, R, ET, *, N>
  ): Column<T, R, ET, P, N> = Column.internalCopy(
    newTable = newTable,
    column = column
  )

  /** Records the SQL occurrence used for a query-graph node. */
  fun recordGraphNode(
    nodeName: String,
    table: Table<*>
  ) {
    tableGraphNodeNames?.put(nodeName, table.nameInQuery)
  }

  /** Records an automatically added table occurrence for selection rewriting. */
  fun recordAutomaticTableOccurrence(table: Table<*>) {
    systemRenamedTables?.let { renamedTables ->
      addTableAlias(
        table = table,
        systemRenamedTables = renamedTables
      )
    }
  }

  /** Adds the left join selected by generated relationship traversal. */
  fun addLeftJoin(
    table: Table<*>,
    on: Expr
  ) {
    from.leftJoin(table.on(on))
    reservedIdentifiers.add(table.nameInQuery)
  }

  internal fun addJoin(join: JoinClause) {
    joins += join
    reservedIdentifiers.add(join.tableNameInQuery)
  }

  internal fun renamedTablesOrNull() = systemRenamedTables?.takeUnless(SimpleArrayMap<*, *>::isEmpty)

  private fun nextAvailableAlias(): String {
    while (true) {
      val alias = "sm_${nextAliasIndex++}"
      if (reservedIdentifiers.add(alias)) {
        return alias
      }
    }
  }
}
