package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.internal.MutableOrderedScatterSet
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.internal.toTypedArray

internal class QueryDependencies private constructor(
  val directSources: List<SqliteQuerySource>,
  val observedTables: Array<String>,
  val directSourcesComplete: Boolean
) {
  internal class Builder(initialCapacity: Int = 0) {
    private val directSourcesByIdentity = LinkedHashMap<SqliteSchemaKey, SqliteQuerySource>()
    private val observedTableNames = MutableOrderedScatterSet<String>(initialCapacity = initialCapacity)
    private var directSourcesComplete = true

    fun addSource(source: SqliteQuerySource) = apply {
      addDirectSource(source)
      if (source.kind == TABLE) {
        addObservedTable(source.name)
      }
    }

    fun addObservedTable(name: String) = apply {
      observedTableNames += name
    }

    fun addObservedTables(names: Array<out String>) = apply {
      names.forEach(observedTableNames::add)
    }

    fun merge(dependencies: QueryDependencies) = apply {
      dependencies.directSources.forEach(::addDirectSource)
      addObservedTables(dependencies.observedTables)
      if (!dependencies.directSourcesComplete) {
        markDirectSourcesIncomplete()
      }
    }

    fun markDirectSourcesIncomplete() = apply {
      directSourcesComplete = false
    }

    fun build() = QueryDependencies(
      directSources = directSourcesByIdentity.values.toList(),
      observedTables = observedTableNames.toTypedArray(),
      directSourcesComplete = directSourcesComplete
    )

    private fun addDirectSource(source: SqliteQuerySource) {
      val normalizedKey = source.identity.normalizedKey
      val previous = directSourcesByIdentity[normalizedKey]
      require(previous == null || previous.kind == source.kind) {
        "Conflicting SQLite source kinds for ${source.identity}: ${previous?.kind} and ${source.kind}"
      }
      if (previous == null) {
        directSourcesByIdentity[normalizedKey] = source
      }
    }
  }
}
