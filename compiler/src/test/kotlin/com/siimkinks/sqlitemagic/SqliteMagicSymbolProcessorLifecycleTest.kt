package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.AnnotationNames.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DEBUG
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.hasMessage
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class SqliteMagicSymbolProcessorLifecycleTest {
  private val compiler = SqliteMagicCompiler { emptyList() }

  @Test
  fun `emits debug message when debug option is enabled`() {
    compiler
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

  @Test
  fun `finalizes every processing step exactly once`() {
    val firstStep = RecordingStep()
    val secondStep = RecordingStep()

    SqliteMagicCompilation
      .compile(
        testTable(),
        processingStepsFactory = { listOf(firstStep, secondStep) }
      )
      .isOk()

    assertThat(firstStep.finishCount).isEqualTo(1)
    assertThat(secondStep.finishCount).isEqualTo(1)
  }

  @Test
  fun `does not finalize steps after processing fails`() {
    val recordingStep = RecordingStep()

    SqliteMagicCompilation
      .compile(
        testTable(),
        processingStepsFactory = { listOf(FailingStep, recordingStep) }
      )
      .isOk()

    assertThat(recordingStep.callCount).isEqualTo(0)
    assertThat(recordingStep.finishCount).isEqualTo(0)
  }

  private fun testTable() = SourceFile.kotlin(
    name = "TestTable.kt",
    contents = """
      package test

      import com.siimkinks.sqlitemagic.annotation.Column
      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.Table

      @Table
      class TestTable {
        @Id
        @Column
        val id: Long = 0L
      }
    """
  )

  private class DeferringOnceStep : ProcessingStep {
    var callCount = 0
      private set
    private var deferred = false

    override fun process(resolver: Resolver): ProcessingStepResult {
      callCount++
      return when {
        deferred -> Continue
        else -> {
          deferred = true
          Deferred(
            symbols = resolver
              .getSymbolsWithAnnotation(TABLE_ANNOTATION)
              .toList()
          )
        }
      }
    }
  }

  private class RecordingStep : ProcessingStep {
    var callCount = 0
      private set
    var finishCount = 0
      private set

    override fun process(resolver: Resolver): ProcessingStepResult {
      callCount++
      return Continue
    }

    override fun finish(): ProcessingStepResult {
      finishCount++
      return Continue
    }
  }

  private object FailingStep : ProcessingStep {
    override fun process(resolver: Resolver): ProcessingStepResult = Failed
  }
}
