package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.index.IndexKind
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.TableElement

internal data class CreationOrderedTables(
  val persistent: List<TableElement>,
  val temporary: List<TableElement>
) {
  internal fun sortedIndexes(indexes: Iterable<IndexElement>): List<IndexElement> {
    val ordered = persistent + temporary
    val tableOrder = ordered.withIndex().associate { (index, table) ->
      table.typeKey to index
    }
    val columnOrder = ordered.associate { table ->
      table.typeKey to table.allColumns.withIndex().associate { (index, column) ->
        column.columnName to index
      }
    }
    return indexes.sortedWith(
      compareBy<IndexElement> {
        tableOrder[it.tableTypeKey] ?: Int.MAX_VALUE
      }
        .thenBy { index ->
          when (index.kind) {
            IndexKind.COMPOSITE -> 0
            IndexKind.FIELD -> 1
          }
        }
        .thenBy { index ->
          val positions = columnOrder[index.tableTypeKey].orEmpty()
          index.columns.minOf { positions[it.columnName] ?: Int.MAX_VALUE }
        }
        .thenBy { index ->
          val positions = columnOrder[index.tableTypeKey].orEmpty()
          index.columns.joinToString(separator = ",") { column ->
            (positions[column.columnName] ?: Int.MAX_VALUE).toString()
          }
        }
        .thenBy(IndexElement::name)
        .thenBy(IndexElement::tableName)
    )
  }

  companion object {
    fun from(tables: Iterable<TableElement>): CreationOrderedTables {
      val (temporaryTables, persistentTables) = tables.partition { TEMPORARY in it.options }
      return CreationOrderedTables(
        persistent = persistentTables.sortedForCreation(),
        temporary = temporaryTables.sortedForCreation()
      )
    }
  }
}

private fun Iterable<TableElement>.sortedForCreation(): List<TableElement> {
  val tables = sortedBy(TableElement::declarationOrder)
  val tablesByType = tables.associateBy(TableElement::typeKey)
  val visited = mutableSetOf<String>()
  val visiting = mutableSetOf<String>()
  val sorted = ArrayList<TableElement>(tables.size)

  fun visit(table: TableElement) {
    if (table.typeKey in visited) return
    check(visiting.add(table.typeKey)) { "Cyclic table relationship graph" }
    table.recursiveRelationshipColumns
      .asSequence()
      .mapNotNull(ColumnElement::referencedTableTypeKey)
      .mapNotNull(tablesByType::get)
      .sortedBy(TableElement::declarationOrder)
      .forEach(::visit)
    visiting.remove(table.typeKey)
    visited += table.typeKey
    sorted += table
  }

  tables.forEach(::visit)
  return sorted
}
