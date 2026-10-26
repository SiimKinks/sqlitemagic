package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.internal.MutableOrderedScatterSet
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.internal.toTypedArray

internal class QueryDependencies private constructor(
  val directSources: List<SqliteQuerySource>,
  val observedTables: Array<String>
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
    initialCapacity: Int = 0,
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
    private val observedTableNames = MutableOrderedScatterSet<String>(initialCapacity = initialCapacity)

    fun addSource(source: SqliteQuerySource) {
      addDirectSource(source)
      if (source.kind == TABLE) {
        observedTableNames += source.name
      }
    }

    fun addObservedTables(names: Array<out String>) = names.forEach(observedTableNames::add)

    fun merge(dependencies: QueryDependencies) {
      dependencies.directSources.forEach(::addDirectSource)
      addObservedTables(dependencies.observedTables)
    }

    fun build() = QueryDependencies(
      directSources = directSources ?: emptyList(),
      observedTables = observedTableNames.toTypedArray()
    )

    private fun addDirectSource(source: SqliteQuerySource) {
      if (directSourceKeys?.add(source.identity.normalizedKey) == true) {
        directSources?.add(source)
      }
    }
  }
}
