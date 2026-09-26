package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

@Suppress("UNCHECKED_CAST")
internal class SelectBuilder<S> {
  internal var sqlTreeRoot: SqlNode? = null
  internal var sqlNodeCount = 0
  internal var from: Select.From<*, *, *, *>? = null
  internal var columnsNode: Select.Columns? = null
  internal var columnNode: Select.SingleColumn<*, *>? = null
  internal val args = ArrayList<String?>()
  internal val observedTables = ArrayList<String>()
  internal var deep = false
  internal var dbConnection: DbConnectionImpl? = null
  private var compiled = false

  /**
   * Append the compiled query to the supplied string builder.
   */
  fun appendCompiledQuery(
    sb: StringBuilder,
    parentObservedTables: ArrayList<String>?
  ) {
    val columnNode = this.columnNode
    val select1 = columnNode != null
    val selectFromTables = when {
      select1 -> columnNode.preCompileColumns()
      else -> checkNotNull(columnsNode).preCompileColumns()
    }
    val tableGraphNodeNames = when {
      selectFromTables != null -> SimpleArrayMap<String, String>(selectFromTables.size)
      else -> null
    }
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
    val systemRenamedTables = queryGraphScope.renamedTablesOrNull()
    if (!select1) {
      checkNotNull(columnsNode).compileColumns(systemRenamedTables)
    }
    if (parentObservedTables != null) {
      perfectSelection(
        from = from,
        observedTables = parentObservedTables,
        tableGraphNodeNames = tableGraphNodeNames,
        columnPositions = null
      )
    }
    val sqlTreeRoot = checkNotNull(sqlTreeRoot)
    when {
      systemRenamedTables != null -> SqlCreator.appendSql(sqlTreeRoot, systemRenamedTables, sb)
      else -> SqlCreator.appendSql(sqlTreeRoot, sb)
    }
  }

  // !!! ordering in this method is important !!!
  @CheckResult
  fun <T> build(): CompiledSelect<T, S> {
    if (compiled) {
      throw IllegalStateException("Select statement builder can be compiled only once")
    }
    compiled = true
    val columnNode = this.columnNode
    val select1 = columnNode != null
    val selectFromTables = when {
      select1 -> columnNode.preCompileColumns()
      else -> checkNotNull(columnsNode).preCompileColumns()
    }
    val tableGraphNodeNames = when {
      selectFromTables != null -> SimpleArrayMap<String, String>(selectFromTables.size)
      else -> SimpleArrayMap<String, String>()
    }
    val from = from as Select.From<T, *, *, *>
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
    val systemRenamedTables = queryGraphScope.renamedTablesOrNull()
    val argsSize = args.size

    val sqlTreeRoot = checkNotNull(sqlTreeRoot)
    if (select1) {
      val sql = when {
        systemRenamedTables != null -> SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, systemRenamedTables)
        else -> SqlCreator.getSql(sqlTreeRoot, sqlNodeCount)
      }
      perfectSelection(
        from = from,
        observedTables = observedTables,
        tableGraphNodeNames = tableGraphNodeNames,
        columnPositions = null
      )
      return CompiledSelect1Impl(
        sql = sql,
        args = when {
          argsSize > 0 -> args.toTypedArray()
          else -> null
        },
        dbConnection = dbConnection,
        selectedColumn = columnNode.column as Column<*, T, *, *, *>,
        observedTables = observedTables.toTypedArray()
      )
    }

    val columnPositions = checkNotNull(columnsNode).compileColumns(systemRenamedTables)
    val implicitSelection = columnPositions.isEmpty
    val sql = when {
      systemRenamedTables != null -> SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, systemRenamedTables)
      else -> SqlCreator.getSql(sqlTreeRoot, sqlNodeCount)
    }
    val forcedDeepSelection = perfectSelection(
      from = from,
      observedTables = observedTables,
      tableGraphNodeNames = tableGraphNodeNames,
      columnPositions = columnPositions
    )
    val selectedRoot = !implicitSelection || from.table.hasViewDefinitionPositions
    return CompiledSelectImpl(
      sql = sql,
      args = when {
        argsSize > 0 -> args.toTypedArray()
        else -> null
      },
      table = table,
      dbConnection = dbConnection,
      observedTables = observedTables.toTypedArray(),
      columns = when {
        !selectedRoot -> null
        else -> columnPositions
      },
      tableGraphNodeNames = when {
        !selectedRoot -> null
        else -> tableGraphNodeNames
      },
      queryDeep = deep || forcedDeepSelection
    )
  }

  private fun perfectSelection(
    from: Select.From<*, *, *, *>,
    observedTables: ArrayList<String>,
    tableGraphNodeNames: SimpleArrayMap<String, String>?,
    columnPositions: SimpleArrayMap<String, Int>?
  ): Boolean {
    val joins = from.joins
    val implicitSelection = columnPositions?.isEmpty == true
    val forcedDeepSelection = from.table.perfectSelection(
      observedTables = observedTables,
      tableGraphNodeNames = tableGraphNodeNames,
      columnPositions = columnPositions,
      implicitOffset = 0,
      implicitSelection = implicitSelection
    )
    var implicitOffset = from.table.nrOfColumns
    for (join in joins) {
      join.table.perfectSelection(
        observedTables = observedTables,
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
