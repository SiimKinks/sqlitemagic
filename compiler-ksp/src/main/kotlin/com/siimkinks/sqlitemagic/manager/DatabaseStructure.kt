package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.index.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.index.SqliteSchema.TEMPORARY
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
) {
  companion object {
    fun from(index: IndexElement) = IndexStructure(
      name = index.name,
      indexSql = index.createSql(),
      forTable = index.tableName
    )
  }
}

@Serializable
data class DatabaseStructure(
  val tables: Map<String, TableStructure> = emptyMap(),
  val indices: Map<String, IndexStructure> = emptyMap(),
  val temporaryTables: Map<String, TableStructure> = emptyMap(),
  val temporaryIndices: Map<String, IndexStructure> = emptyMap()
) {
  companion object {
    internal fun from(
      orderedTables: CreationOrderedTables,
      indexes: Iterable<IndexElement> = emptyList()
    ) = with(orderedTables) {
      val orderedIndexes = sortedIndexes(indexes)
      DatabaseStructure(
        tables = persistent.associateByTo(
          destination = linkedMapOf(),
          keySelector = TableElement::tableName,
          valueTransform = TableStructure::from
        ),
        indices = orderedIndexes
          .filter { it.schema == MAIN }
          .associateByTo(
            destination = linkedMapOf(),
            keySelector = IndexElement::name,
            valueTransform = IndexStructure::from
          ),
        temporaryTables = temporary.associateByTo(
          destination = linkedMapOf(),
          keySelector = TableElement::tableName,
          valueTransform = TableStructure::from
        ),
        temporaryIndices = orderedIndexes
          .filter { it.schema == TEMPORARY }
          .associateByTo(
            destination = linkedMapOf(),
            keySelector = IndexElement::name,
            valueTransform = IndexStructure::from
          )
      )
    }
  }

  fun persistentOnly() = DatabaseStructure(
    tables = tables,
    indices = indices
  )

  operator fun plus(other: DatabaseStructure) = DatabaseStructure(
    tables = LinkedHashMap(tables).apply { putAll(other.tables) },
    indices = LinkedHashMap(indices).apply { putAll(other.indices) },
    temporaryTables = LinkedHashMap(temporaryTables).apply { putAll(other.temporaryTables) },
    temporaryIndices = LinkedHashMap(temporaryIndices).apply { putAll(other.temporaryIndices) }
  )
}
