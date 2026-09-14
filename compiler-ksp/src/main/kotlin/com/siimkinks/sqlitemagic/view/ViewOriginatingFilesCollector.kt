package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.siimkinks.sqlitemagic.writer.OriginatingFilesAccumulator

internal class ViewOriginatingFilesCollector(
  private val environment: Environment,
  private val seeds: Map<TypeKey, ViewSeed>
) {
  private val originatingFiles = OriginatingFilesAccumulator()
  private val visitedViews = mutableSetOf<TypeKey>()

  fun collect(seed: ViewSeed): OriginatingFiles {
    add(seed)
    return originatingFiles.build()
  }

  private fun add(seed: ViewSeed) {
    if (!visitedViews.add(seed.typeKey)) return
    originatingFiles.add(seed.declaration.containingFile)
    originatingFiles.add(seed.query.sourceDeclaration.containingFile)
    seed.properties.forEach(::add)
  }

  private fun add(property: ViewPropertySeed) {
    val roundElement = property.roundElement
    originatingFiles.add(roundElement.sourceDeclaration.containingFile)
    originatingFiles.add(roundElement.declaration?.containingFile)
    when (property) {
      is ViewEmbeddedSeed -> property.properties.forEach(::add)
      is ViewLeafSeed -> when {
        roundElement.sqlStorageType != null -> Unit
        environment.transformerElements[roundElement.typeKey] != null -> addTransformer(roundElement.typeKey)
        environment.tableElements[roundElement.typeKey] != null -> addTable(roundElement.typeKey)
        seeds[roundElement.typeKey] != null -> add(checkNotNull(seeds[roundElement.typeKey]))
        environment.viewElements[roundElement.typeKey] != null -> originatingFiles.markIncomplete()
      }
    }
  }

  private fun addTransformer(typeKey: TypeKey) {
    val transformer = environment.getRoundTransformerFor(typeKey)
    when {
      transformer == null -> originatingFiles.markIncomplete()
      else -> originatingFiles.add(transformer.originatingFiles)
    }
  }

  private fun addTable(typeKey: TypeKey) {
    val table = environment.tableRoundElementsForCurrentRound
      .firstOrNull { it.table.typeKey == typeKey }
    when {
      table == null -> originatingFiles.markIncomplete()
      else -> {
        originatingFiles.add(table.originatingFiles)
      }
    }
  }
}
