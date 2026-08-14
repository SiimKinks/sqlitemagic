package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.schemaSql
import kotlinx.serialization.Serializable

@Serializable
data class ColumnStructure(
  val id: Boolean = false,
  val autoIncrement: Boolean = false,
  val name: String = "",
  val onDeleteCascade: Boolean = false,
  val sqlType: String = "",
  val schema: String = ""
) {
  companion object {
    fun from(column: ColumnElement) = with(column) {
      ColumnStructure(
        id = isId,
        autoIncrement = id?.isAutoIncrement == true,
        name = columnName,
        onDeleteCascade = relationship?.onDeleteCascade == true,
        sqlType = sqlStorageType.affinity.name,
        schema = schemaSql()
      )
    }
  }
}

@Serializable
data class TableStructure(
  val name: String = "",
  val schema: String = "",
  val columns: List<ColumnStructure> = emptyList()
) {
  companion object {
    fun from(table: TableElement) = with(table) {
      val columns = allColumns.map(transform = ColumnStructure::from)
      TableStructure(
        name = tableName,
        schema = schemaSql(
          columnSchemas = columns.map(ColumnStructure::schema)
        ),
        columns = columns
      )
    }
  }
}

@Serializable
data class IndexStructure(
  val name: String = "",
  val indexSql: String = "",
  val forTable: String = ""
)

@Serializable
data class DatabaseStructure(
  val tables: LinkedHashMap<String, TableStructure> = linkedMapOf(),
  val indices: LinkedHashMap<String, IndexStructure> = linkedMapOf()
) {
  companion object {
    internal fun from(orderedTables: CreationOrderedTables) = with(orderedTables) {
      DatabaseStructure(
        tables = persistent.associateByTo(
          destination = linkedMapOf(),
          keySelector = TableElement::tableName,
          valueTransform = TableStructure::from
        )
      )
    }
  }

  operator fun plus(other: DatabaseStructure) = DatabaseStructure(
    tables = LinkedHashMap(tables).apply { putAll(other.tables) },
    indices = LinkedHashMap(indices).apply { putAll(other.indices) }
  )
}
