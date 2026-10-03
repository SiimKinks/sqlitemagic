package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

class ViewDefinition internal constructor(
  val sql: String,
  val args: Array<String?>?,
  internal val queryDependencies: QueryDependencies,
  private val columns: MutableObjectIntMap<String>?,
  private val tableGraphNodeNames: MutableScatterMap<String, String>?,
  val queryDeep: Boolean
) {
  internal val hasColumnPositions get() = columns?.isNotEmpty() == true

  internal fun contributeTo(
    tableIdentifier: String,
    tableGraphNodeNames: MutableScatterMap<String, String>?,
    columnPositions: MutableObjectIntMap<String>?,
    implicitOffset: Int,
    implicitSelection: Boolean
  ) {
    when {
      columnPositions == null -> null
      implicitSelection -> implicitOffset
      else -> columnPositions.getOrNull(tableIdentifier)
    }?.let { offset ->
      val source = columns
      when {
        source?.isNotEmpty() == true -> {
          columnPositions?.remove(tableIdentifier)
          source.forEach { name, position ->
            columnPositions?.put(
              key = "$tableIdentifier.$name",
              value = offset + position
            )
          }
        }
        else -> columnPositions?.put(
          key = tableIdentifier,
          value = offset
        )
      }
    }
    if (tableGraphNodeNames != null) {
      this.tableGraphNodeNames?.let { source ->
        source.forEach { name, graphNode ->
          tableGraphNodeNames.put(
            key = "$tableIdentifier.$name",
            value = graphNode
          )
        }
      }
    }
  }
}
