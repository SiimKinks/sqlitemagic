package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

class ViewDefinition(
  val sql: String,
  val args: Array<String?>?,
  private val observedTables: Array<String>,
  private val columns: SimpleArrayMap<String, Int>?,
  private val tableGraphNodeNames: SimpleArrayMap<String, String>?,
  val queryDeep: Boolean
) {
  internal val hasColumnPositions get() = columns?.isEmpty == false

  internal fun contributeTo(
    tableIdentifier: String,
    observedTables: ArrayList<String>,
    tableGraphNodeNames: SimpleArrayMap<String, String>?,
    columnPositions: SimpleArrayMap<String, Int>?,
    implicitOffset: Int,
    implicitSelection: Boolean
  ) {
    addObservedTablesTo(observedTables)
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

  private fun addObservedTablesTo(destination: ArrayList<String>) {
    destination.ensureCapacity(destination.size + observedTables.size)
    observedTables.forEach { table ->
      if (table !in destination) {
        destination.add(table)
      }
    }
  }
}
