package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList

@Suppress("UNCHECKED_CAST")
internal class SelectBuilder<S> {
  internal var sqlTreeRoot: SqlNode? = null
  internal var sqlNodeCount = 0
  internal var from: Select.From<*, *, *, *>? = null
  internal var columnsNode: Select.Columns? = null
  internal var columnNode: Select.SingleColumn<*, *>? = null
  internal val args = ArrayList<String?>()
  internal val dependencies = QueryDependencies.Builder()
  internal var deep = false
  internal var dbConnection: DbConnectionImpl? = null
  private var compiled = false
  private var frozen = false
  private var preparedQuery: PreparedQuery? = null
  private var fragment: QueryFragment? = null

  private class PreparedQuery(
    val tableGraphNodeNames: SimpleArrayMap<String, String>,
    val systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>?,
    val dependencies: QueryDependencies
  )

  fun ensureMutable() = check(!frozen && !compiled) {
    "Cannot mutate an embedded or frozen select statement"
  }

  fun freezeFragment(): QueryFragment {
    fragment?.let { return it }
    check(!compiled) { "Select statement builder can be compiled only once" }
    val prepared = prepareQuery()
    if (columnNode == null) {
      checkNotNull(columnsNode).compileColumns(prepared.systemRenamedTables)
    }
    val sqlTreeRoot = checkNotNull(sqlTreeRoot)
    val sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, prepared.systemRenamedTables)
    return QueryFragment(
      sql = sql,
      args = args,
      dependencies = prepared.dependencies
    ).also {
      fragment = it
      frozen = true
    }
  }

  // !!! ordering in this method is important !!!
  @CheckResult
  fun <T> build(): CompiledSelect<T, S> {
    check(!compiled) { "Select statement builder can be compiled only once" }
    check(!frozen) { "Cannot compile an embedded or frozen select statement" }
    val prepared = prepareQuery()
    compiled = true
    val columnNode = this.columnNode
    val select1 = columnNode != null
    val tableGraphNodeNames = prepared.tableGraphNodeNames
    val from = from as Select.From<T, *, *, *>
    val table = from.table
    val systemRenamedTables = prepared.systemRenamedTables
    val argsSize = args.size
    val sqlTreeRoot = checkNotNull(sqlTreeRoot)
    val typedArgs = when {
      argsSize > 0 -> args.toTypedArray()
      else -> null
    }
    if (select1) {
      val sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, systemRenamedTables)
      perfectSelection(
        from = from,
        tableGraphNodeNames = tableGraphNodeNames,
        columnPositions = null
      )
      return CompiledSelect1Impl(
        sql = sql,
        args = typedArgs,
        dbConnection = dbConnection,
        selectedColumn = columnNode.column as Column<*, T, *, *, *>,
        queryDependencies = prepared.dependencies
      )
    }

    val columnPositions = checkNotNull(columnsNode).compileColumns(systemRenamedTables)
    val implicitSelection = columnPositions.isEmpty
    val sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, systemRenamedTables)
    val forcedDeepSelection = perfectSelection(
      from = from,
      tableGraphNodeNames = tableGraphNodeNames,
      columnPositions = columnPositions
    )
    val selectedRoot = !implicitSelection || from.table.hasViewDefinitionPositions
    return CompiledSelectImpl(
      sql = sql,
      args = typedArgs,
      table = table,
      dbConnection = dbConnection,
      queryDependencies = prepared.dependencies,
      columns = columnPositions.takeIf { selectedRoot },
      tableGraphNodeNames = tableGraphNodeNames.takeIf { selectedRoot },
      queryDeep = deep || forcedDeepSelection
    )
  }

  private fun prepareQuery(): PreparedQuery {
    preparedQuery?.let { return it }
    val columnNode = this.columnNode
    val select1 = columnNode != null
    val selectFromTables = when {
      select1 -> columnNode.preCompileColumns()
      else -> checkNotNull(columnsNode).preCompileColumns()
    }
    val tableGraphNodeNames = SimpleArrayMap<String, String>(selectFromTables?.size ?: 0)
    val from = checkNotNull(from)
    val table = from.table
    val queryGraphScope = QueryGraphScope(
      from = from,
      selectFromTables = selectFromTables,
      tableGraphNodeNames = tableGraphNodeNames,
      queryDeep = deep,
      select1 = select1
    )
    queryGraphScope.visit(
      table = table,
      tableAlias = table,
      nodeName = ""
    )
    val collectedDependencies = QueryDependencies.Builder()
    from.table.addDependencies(collectedDependencies)
    from.joins.forEach { it.table.addDependencies(collectedDependencies) }
    collectedDependencies.merge(dependencies.build())
    return PreparedQuery(
      tableGraphNodeNames = tableGraphNodeNames,
      systemRenamedTables = queryGraphScope.renamedTablesOrNull(),
      dependencies = collectedDependencies.build()
    ).also { preparedQuery = it }
  }

  private fun perfectSelection(
    from: Select.From<*, *, *, *>,
    tableGraphNodeNames: SimpleArrayMap<String, String>?,
    columnPositions: SimpleArrayMap<String, Int>?
  ): Boolean {
    val joins = from.joins
    val implicitSelection = columnPositions?.isEmpty == true
    val forcedDeepSelection = from.table.perfectSelection(
      tableGraphNodeNames = tableGraphNodeNames,
      columnPositions = columnPositions,
      implicitOffset = 0,
      implicitSelection = implicitSelection
    )
    var implicitOffset = from.table.nrOfColumns
    for (join in joins) {
      join.table.perfectSelection(
        tableGraphNodeNames = tableGraphNodeNames,
        columnPositions = columnPositions,
        implicitOffset = implicitOffset,
        implicitSelection = implicitSelection
      )
      implicitOffset += join.table.nrOfColumns
    }
    return forcedDeepSelection
  }
}
