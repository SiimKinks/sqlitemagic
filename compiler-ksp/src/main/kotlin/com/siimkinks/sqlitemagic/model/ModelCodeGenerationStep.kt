package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.writer.ColumnClassWriter
import java.io.IOException

class ModelCodeGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  private val generatedUniqueTransformerColumns = mutableSetOf<String>()
  private val generatedRelationshipColumns = mutableSetOf<String>()

  private val writers = listOf(
    ModelDaoWriter(environment),
    ModelTableWriter(environment),
    ModelExtensionsWriter(environment),
  )

  override fun process(resolver: Resolver): ProcessingStepResult {
    for (roundTable in environment.tableRoundElementsForCurrentRound) {
      try {
        generateUniqueTransformerColumns(roundTable)
        generateRelationshipColumns(roundTable)
        writers.forEach { it.write(roundTable) }
      } catch (exception: IOException) {
        environment.logger.exception(exception)
        return Failed
      }
    }
    return Continue
  }

  private fun generateUniqueTransformerColumns(roundTable: TableRoundElement) {
    for (column in roundTable.table.allColumns) {
      val transformer = column.transformer ?: continue
      when {
        !column.isUnique && !column.isId -> continue
        !generatedUniqueTransformerColumns.add(transformer.transformerName) -> continue
      }
      ColumnClassWriter
        .from(
          transformerElement = transformer,
          codeGenerator = environment.codeGenerator,
          createUniqueClass = true
        )
        .write(
          // This shared class depends on every table that can require the transformer.
          roundTable.originatingFiles.copy(isComplete = false)
        )
    }
  }

  private fun generateRelationshipColumns(roundTable: TableRoundElement) {
    val table = roundTable.table
    for (column in table.relationshipColumns) {
      if (!column.hasGeneratedColumnClass) continue
      val className = table.relationshipColumnClassName(column)
      if (!generatedRelationshipColumns.add(className.canonicalName)) continue
      ColumnClassWriter
        .fromRelationship(
          table = table,
          column = column,
          codeGenerator = environment.codeGenerator
        )
        .write(roundTable.originatingFiles)
    }
  }
}
