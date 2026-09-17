package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

@Suppress("UNCHECKED_CAST")
internal class SelectBuilder<S> {
  @JvmField
  var sqlTreeRoot: SqlNode? = null

  @JvmField
  var sqlNodeCount = 0

  @JvmField
  var from: Select.From<*, *, *, *>? = null

  @JvmField
  var columnsNode: Select.Columns? = null

  @JvmField
  var columnNode: Select.SingleColumn<*, *>? = null

  @JvmField
  val args = ArrayList<String>()

  @JvmField
  val observedTables = ArrayList<String>()

  @JvmField
  var deep = false

  @JvmField
  var dbConnection: DbConnectionImpl? = null

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
    val systemRenamedTables = when {
      deep -> table.addDeepQueryParts(from, selectFromTables, tableGraphNodeNames, select1)
      else -> table.addShallowQueryParts(from, selectFromTables, tableGraphNodeNames, select1)
    }
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
    val systemRenamedTables = when {
      deep -> table.addDeepQueryParts(from, selectFromTables, tableGraphNodeNames, select1)
      else -> table.addShallowQueryParts(from, selectFromTables, tableGraphNodeNames, select1)
    }
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
    val fromSelection = columnPositions.isEmpty
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
        fromSelection -> null
        else -> columnPositions
      },
      tableGraphNodeNames = when {
        fromSelection -> null
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
    var forcedDeepSelection = from.table.perfectSelection(observedTables, tableGraphNodeNames, columnPositions)
    for (join in joins) {
      forcedDeepSelection = forcedDeepSelection or join.table.perfectSelection(
        observedTables,
        tableGraphNodeNames,
        columnPositions
      )
    }
    return forcedDeepSelection
  }
}
