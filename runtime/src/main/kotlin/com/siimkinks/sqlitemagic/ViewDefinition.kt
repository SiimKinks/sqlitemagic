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
  /** Append the defining query's observed tables to a generated table's destination. */
  fun addObservedTablesTo(destination: ArrayList<String>) {
    destination.ensureCapacity(destination.size + observedTables.size)
    observedTables.forEach(destination::add)
  }

  /** Merge the defining query's selection positions into a generated table's destination. */
  fun putColumnsInto(destination: SimpleArrayMap<String, Int>?) {
    if (destination != null) {
      columns?.let(destination::putAll)
    }
  }

  /** Merge the defining query's selection graph into a generated table's destination. */
  fun putTableGraphNodeNamesInto(destination: SimpleArrayMap<String, String>?) {
    if (destination != null) {
      tableGraphNodeNames?.let(destination::putAll)
    }
  }
}
