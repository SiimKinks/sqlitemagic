package com.siimkinks.sqlitemagic.manager

internal class ForeignKeyGraph(
  private val tableNames: Iterable<String>,
  private val referencesByTableName: Map<String, Set<String>>
) {
  private val dependentsByReferencedName = buildDependentsByReferencedName()

  fun dependentRebuildClosure(
    previousNamesByCurrentName: Map<String, String>,
    changedTables: Set<String>,
    excludedTables: Set<String>
  ): Set<String> {
    if (changedTables.isEmpty()) return emptySet()

    val result = changedTables.toMutableSet()
    val normalizedResult = hashSetOf<String>()
    val pendingReferences = ArrayDeque<String>()

    fun enqueueReference(normalizedName: String) {
      if (normalizedResult.add(normalizedName)) {
        pendingReferences.addLast(normalizedName)
      }
    }

    fun enqueueReferences(tableName: String) {
      enqueueReference(tableName.normalizedSqlIdentifier())
      previousNamesByCurrentName[tableName]
        ?.normalizedSqlIdentifier()
        ?.let(::enqueueReference)
    }

    changedTables.forEach(::enqueueReferences)
    var hasDependents = result.any { tableName ->
      referencesByTableName
        .getValue(tableName)
        .any(normalizedResult::contains)
    }

    while (pendingReferences.isNotEmpty()) {
      val referencedName = pendingReferences.removeFirst()
      for (dependentName in dependentsByReferencedName[referencedName].orEmpty()) {
        when (dependentName) {
          in excludedTables -> enqueueReferences(dependentName)
          else -> {
            hasDependents = true
            if (result.add(dependentName)) {
              enqueueReferences(dependentName)
            }
          }
        }
      }
    }
    return when {
      hasDependents -> result
      else -> emptySet()
    }
  }

  fun transitiveDependents(tableName: String) = buildSet {
    val pendingReferences = ArrayDeque<String>()
    pendingReferences.addLast(tableName.normalizedSqlIdentifier())
    while (pendingReferences.isNotEmpty()) {
      for (dependentName in dependentsByReferencedName[pendingReferences.removeFirst()].orEmpty()) {
        if (add(dependentName)) {
          pendingReferences.addLast(dependentName.normalizedSqlIdentifier())
        }
      }
    }
  }

  fun orderReferencesFirst(tableNamesToOrder: Iterable<String>): List<String> {
    val tableNamesByNormalizedName = tableNamesToOrder.associateBy(String::normalizedSqlIdentifier)
    val result = linkedSetOf<String>()
    val visiting = hashSetOf<String>()

    fun visit(tableName: String) {
      if (tableName in result || !visiting.add(tableName)) return
      referencesByTableName
        .getValue(tableName)
        .mapNotNull(tableNamesByNormalizedName::get)
        .forEach(::visit)
      visiting -= tableName
      result += tableName
    }

    tableNamesToOrder.forEach(::visit)
    return result.toList()
  }

  private fun buildDependentsByReferencedName() = buildMap {
    tableNames.forEach { tableName ->
      referencesByTableName
        .getValue(tableName)
        .forEach { referencedName ->
          getOrPut(
            key = referencedName,
            defaultValue = ::arrayListOf
          ).add(tableName)
        }
    }
  }
}
