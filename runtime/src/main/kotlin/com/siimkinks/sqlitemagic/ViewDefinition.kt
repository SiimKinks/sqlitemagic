package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

class ViewDefinition internal constructor(
  val sql: String,
  val args: Array<String?>?,
  internal val queryDependencies: QueryDependencies,
  private val columns: SimpleArrayMap<String, Int>?,
  private val tableGraphNodeNames: SimpleArrayMap<String, String>?,
  val queryDeep: Boolean
) {
  internal val hasColumnPositions get() = columns?.isEmpty == false

  internal fun contributeTo(
    tableIdentifier: String,
    tableGraphNodeNames: SimpleArrayMap<String, String>?,
    columnPositions: SimpleArrayMap<String, Int>?,
    implicitOffset: Int,
    implicitSelection: Boolean
  ) {
    when {
      columnPositions == null -> null
      implicitSelection -> implicitOffset
      else -> columnPositions[tableIdentifier]
    }?.let { offset ->
      val source = columns
      when {
        source?.isEmpty == false -> {
          columnPositions?.remove(tableIdentifier)
          for (index in 0 until source.size()) {
            columnPositions?.put("$tableIdentifier.${source.keyAt(index)}", offset + source.valueAt(index))
          }
        }
        else -> columnPositions?.put(tableIdentifier, offset)
      }
    }
    if (tableGraphNodeNames != null) {
      this.tableGraphNodeNames?.let { source ->
        for (index in 0 until source.size()) {
          tableGraphNodeNames.put("$tableIdentifier.${source.keyAt(index)}", source.valueAt(index))
        }
      }
    }
  }
}
