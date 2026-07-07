package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DEBUG
import com.siimkinks.sqlitemagic.Types.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.testTable
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SqliteMagicSymbolProcessorLifecycleTest {
  @Test
  fun `compiles sources annotated with supported annotations`() {
    SqliteMagicCompilation
      .compile(testTable())
      .isOk()
  }

  @Test
  fun `emits debug message when debug option is enabled`() {
    SqliteMagicCompilation
      .compile(
        testTable(),
        kspOptions = mapOf(OPTION_DEBUG to "true")
      )
      .isOk()
      .hasMessage("SqliteMagic KSP debug mode enabled")
  }

  @Test
  fun `continues processing remaining steps when a step defers symbols`() {
    val deferringStep = DeferringOnceStep()
    val recordingStep = RecordingStep()
    SqliteMagicCompilation
      .compile(
        testTable(),
        processingStepsFactory = { listOf(deferringStep, recordingStep) }
      )
      .isOk()
    assertThat(deferringStep.callCount).isEqualTo(1)
    assertThat(recordingStep.callCount).isEqualTo(1)
  }

  private class DeferringOnceStep : ProcessingStep {
    var callCount = 0
    var deferred = false

    override fun process(resolver: Resolver): ProcessingStepResult {
      callCount++
      if (deferred) {
        return Continue
      }
      deferred = true
      return Deferred(
        symbols = resolver
          .getSymbolsWithAnnotation(TABLE_ANNOTATION)
          .toList()
      )
    }
  }

  private class RecordingStep : ProcessingStep {
    var callCount = 0

    override fun process(resolver: Resolver): ProcessingStepResult {
      callCount++
      return Continue
    }
  }
}
