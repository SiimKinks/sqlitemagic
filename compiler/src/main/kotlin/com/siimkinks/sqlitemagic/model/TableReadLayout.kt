package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.TypeKey

/** The number of cursor values consumed by a generated table reader. */
internal class TableReadLayout(
  private val tables: Map<TypeKey, TableElement>
) {
  fun width(
    table: TableElement,
    recursive: Boolean
  ) = table.allColumns.size + table.relationshipColumns.sumOf {
    relationshipWidth(
      column = it,
      recursive = recursive
    )
  }

  fun relationshipWidth(
    column: ColumnElement,
    recursive: Boolean
  ): Int {
    val relationship = column.relationship ?: return 0
    return when {
      !relationship.isHandledRecursively -> 0
      !recursive && relationship.canConstructWithOnlyId -> 0
      else -> {
        val target = checkNotNull(tables[relationship.referencedTableTypeKey]) {
          "Missing relationship table ${relationship.referencedTableTypeKey}"
        }
        width(
          table = target,
          recursive = recursive
        )
      }
    }
  }
}
