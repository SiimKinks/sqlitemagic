package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.siimkinks.sqlitemagic.writer.OriginatingFilesAccumulator

internal class OriginatingFilesCollector(
  private val environment: Environment,
  private val tableSeeds: Map<TypeKey, TableSeed>
) {
  private val originatingFiles = OriginatingFilesAccumulator()

  fun collect(tableSeed: TableSeed): OriginatingFiles {
    add(
      tableSeed = tableSeed,
      visitedTableTypes = mutableSetOf()
    )
    return originatingFiles.build()
  }

  private fun add(
    tableSeed: TableSeed,
    visitedTableTypes: MutableSet<TypeKey>
  ) {
    if (!visitedTableTypes.add(tableSeed.typeKey)) return
    originatingFiles.add(tableSeed.classDeclaration.containingFile)
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
    originatingFiles.add(propertySeed.roundElement.sourceDeclaration.containingFile)
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
      originatingFiles.add(columnSeed.roundElement.declaration?.containingFile)
      val targetSeed = tableSeeds[relationshipTypeKey]
      when {
        targetSeed == null -> originatingFiles.markIncomplete()
        columnSeed.isHandledRecursively -> add(
          tableSeed = targetSeed,
          visitedTableTypes = visitedTableTypes
        )
        else -> targetSeed.idSeed
          ?.let { targetId ->
            originatingFiles.add(targetId.roundElement.sourceDeclaration.containingFile)
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
    originatingFiles.add(valueDeclaration?.containingFile)
    val roundTransformer = environment.getRoundTransformerFor(transformer.typeKey)
    when {
      roundTransformer == null -> originatingFiles.markIncomplete()
      else -> originatingFiles.add(roundTransformer.originatingFiles)
    }
  }
}
