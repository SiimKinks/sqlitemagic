package com.siimkinks.sqlitemagic.manager

import java.util.ArrayDeque

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
        if (dependentName in excludedTables || !result.add(dependentName)) continue

        hasDependents = true
        enqueueReferences(dependentName)
      }
    }
    return when {
      hasDependents -> result
      else -> emptySet()
    }
  }

  fun orderDependentsFirst(tableNamesToOrder: Set<String>): List<String> {
    val result = linkedSetOf<String>()
    val visiting = hashSetOf<String>()

    fun addWithDependents(tableName: String) {
      val pending = ArrayDeque<RemovedTableVisit>()
      pending.addLast(
        RemovedTableVisit(
          tableName = tableName,
          expanded = false
        )
      )
      while (pending.isNotEmpty()) {
        val visit = pending.removeLast()
        if (visit.expanded) {
          visiting -= visit.tableName
          result += visit.tableName
          continue
        }
        if (visit.tableName in result || !visiting.add(visit.tableName)) continue

        pending.addLast(
          RemovedTableVisit(
            tableName = visit.tableName,
            expanded = true
          )
        )
        dependentsByReferencedName[visit.tableName.normalizedSqlIdentifier()]
          .orEmpty()
          .asReversed()
          .forEach { dependentTableName ->
            pending.addLast(
              RemovedTableVisit(
                tableName = dependentTableName,
                expanded = false
              )
            )
          }
      }
    }

    tableNamesToOrder
      .asSequence()
      .filterNot(result::contains)
      .forEach(::addWithDependents)
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

private data class RemovedTableVisit(
  val tableName: String,
  val expanded: Boolean
)
