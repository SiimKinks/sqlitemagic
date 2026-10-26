package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.writer.OriginatingFiles

internal fun ProcessingStep.observing(record: (ProcessingStepResult) -> Unit): ProcessingStep =
  object : ProcessingStep by this {
    override fun process(resolver: Resolver) = this@observing
      .process(resolver)
      .also(record)
  }

internal fun ProcessingStepResult.deferredNames() = when (this) {
  is Deferred -> symbols
    .filterIsInstance<KSDeclaration>()
    .map(KSDeclaration::simpleName)
    .map(KSName::asString)
  else -> emptyList()
}

internal data class RoundState(
  val collected: List<String>,
  val deferred: List<String>
)

internal fun ProcessingStep.recordingRounds(
  rounds: MutableList<RoundState>,
  collected: () -> List<String>
) = observing { result ->
  rounds += RoundState(
    collected = collected(),
    deferred = result.deferredNames()
  )
}

internal fun List<ProcessingStep>.insertBeforeLast(step: ProcessingStep) = dropLast(1) + step + last()

internal inline fun <reified T : ProcessingStep> List<ProcessingStep>.insertAfter(step: ProcessingStep) =
  (indexOfFirst { it is T } + 1).let { index -> take(index) + step + drop(index) }

internal data class OriginatingFilesSnapshot(
  val files: Set<String>,
  val isComplete: Boolean
)

internal class OriginRecordingStep(
  private val snapshots: MutableMap<String, OriginatingFilesSnapshot>,
  private val roundOrigins: () -> Map<String, OriginatingFiles>
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    roundOrigins().forEach { (modelName, origins) ->
      snapshots[modelName] = OriginatingFilesSnapshot(
        files = origins.files
          .map(KSFile::fileName)
          .toSet(),
        isComplete = origins.isComplete
      )
    }
    return Continue
  }
}
