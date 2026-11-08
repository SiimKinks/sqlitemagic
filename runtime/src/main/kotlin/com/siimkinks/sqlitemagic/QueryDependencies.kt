package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.internal.MutableOrderedScatterSet
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.internal.toTypedArray

internal class QueryDependencies private constructor(
  val directSources: List<SqliteQuerySource>,
  val observedTables: Array<String>,
  val declaresSources: Boolean,
  val readsTemporaryObjects: Boolean
) {
  companion object {
    val NONE = Builder().build()

    fun declared(table: Table<*>, tables: Array<out Table<*>>) = Builder(initialCapacity = tables.size + 1)
      .apply {
        table.addDependencies(this)
        tables.forEach { it.addDependencies(this) }
      }
      .build()
  }

  class Builder(
    private val initialCapacity: Int = 0,
    collectDirectSources: Boolean = true
  ) {
    private val directSources = when {
      collectDirectSources -> ArrayList<SqliteQuerySource>(initialCapacity)
      else -> null
    }
    private val directSourceKeys = when {
      collectDirectSources -> MutableScatterSet<SqliteSchemaKey>(initialCapacity)
      else -> null
    }
    private var firstObservedTable: String? = null
    private var observedTableNames: MutableOrderedScatterSet<String>? = null
    private var declaresSources = false
    private var readsTemporaryObjects = false

    fun addSource(source: SqliteQuerySource) {
      declaresSources = true
      if (source.schema == TEMPORARY) {
        readsTemporaryObjects = true
      }
      addDirectSource(source)
      if (source.kind == TABLE) {
        addObservedTable(source.name)
      }
    }

    fun addObservedTables(names: Array<out String>) = names.forEach(::addObservedTable)

    fun merge(dependencies: QueryDependencies) {
      declaresSources = declaresSources || dependencies.declaresSources
      readsTemporaryObjects = readsTemporaryObjects || dependencies.readsTemporaryObjects
      dependencies.directSources.forEach(::addDirectSource)
      addObservedTables(dependencies.observedTables)
    }

    fun build() = QueryDependencies(
      directSources = directSources.orEmpty(),
      observedTables = observedTableNames?.toTypedArray() ?: firstObservedTable?.let(::arrayOf) ?: NO_TABLES,
      declaresSources = declaresSources,
      readsTemporaryObjects = readsTemporaryObjects
    )

    private fun addObservedTable(name: String) {
      val first = firstObservedTable
      when {
        first == null -> firstObservedTable = name
        first == name -> Unit
        else -> (observedTableNames ?: startObservedTableNames(first)).add(name)
      }
    }

    private fun startObservedTableNames(first: String) = MutableOrderedScatterSet<String>(
      initialCapacity = initialCapacity
    ).also { names ->
      names += first
      observedTableNames = names
    }

    private fun addDirectSource(source: SqliteQuerySource) {
      if (directSourceKeys?.add(source.identity.normalizedKey) == true) {
        directSources?.add(source)
      }
    }
  }
}

private val NO_TABLES = emptyArray<String>()
