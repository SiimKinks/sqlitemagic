package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.writer.OriginatingFiles

internal class OriginatingFilesCollector(
  private val environment: Environment,
  private val tableSeeds: Map<TypeKey, TableSeed>
) {
  private val files = linkedSetOf<KSFile>()
  private var isComplete = true

  fun collect(tableSeed: TableSeed): OriginatingFiles {
    add(
      tableSeed = tableSeed,
      visitedTableTypes = mutableSetOf()
    )
    return OriginatingFiles(
      files = files.toSet(),
      isComplete = isComplete
    )
  }

  private fun add(
    tableSeed: TableSeed,
    visitedTableTypes: MutableSet<TypeKey>
  ) {
    if (!visitedTableTypes.add(tableSeed.typeKey)) return
    add(tableSeed.classDeclaration.containingFile)
    tableSeed.propertySeeds.forEach { property ->
      add(
        propertySeed = property,
        visitedTableTypes = visitedTableTypes
      )
    }
  }

  private fun add(
    propertySeed: PropertySeed,
    visitedTableTypes: MutableSet<TypeKey>
  ) {
    add(propertySeed.roundElement.sourceDeclaration.containingFile)
    when (propertySeed) {
      is ColumnSeed -> add(
        columnSeed = propertySeed,
        visitedTableTypes = visitedTableTypes
      )
      is EmbeddedSeed -> propertySeed.properties.forEach { property ->
        add(
          propertySeed = property,
          visitedTableTypes = visitedTableTypes
        )
      }
    }
  }

  private fun add(
    columnSeed: ColumnSeed,
    visitedTableTypes: MutableSet<TypeKey>
  ) {
    val relationshipTypeKey = columnSeed.relationshipTypeKey
    if (relationshipTypeKey != null) {
      add(columnSeed.roundElement.declaration?.containingFile)
      val targetSeed = tableSeeds[relationshipTypeKey]
      when {
        targetSeed == null -> isComplete = false
        columnSeed.isHandledRecursively -> add(
          tableSeed = targetSeed,
          visitedTableTypes = visitedTableTypes
        )
        else -> targetSeed.idSeed
          ?.let { targetId ->
            add(targetId.roundElement.sourceDeclaration.containingFile)
            targetId.transformer?.let { transformer ->
              add(
                transformer = transformer,
                valueDeclaration = targetId.roundElement.declaration
              )
            }
          }
      }
    }
    columnSeed.transformer?.let { transformer ->
      add(
        transformer = transformer,
        valueDeclaration = columnSeed.roundElement.declaration
      )
    }
  }

  private fun add(
    transformer: TransformerElement,
    valueDeclaration: KSClassDeclaration?
  ) {
    add(valueDeclaration?.containingFile)
    val roundTransformer = environment.getRoundTransformerFor(transformer.typeKey)
    when {
      roundTransformer == null -> isComplete = false
      else -> files += roundTransformer.originatingFiles
    }
  }

  private fun add(file: KSFile?) {
    if (file != null) files += file
  }
}
