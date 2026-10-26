package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import java.io.File

internal class SqliteMagicCompiler(
  private val processingSteps: (Environment) -> List<ProcessingStep>
) {
  fun compile(
    vararg sources: SourceFile,
    kspOptions: Map<String, String> = emptyMap(),
    classpaths: List<File> = emptyList()
  ) = SqliteMagicCompilation.compile(
    *sources,
    kspOptions = kspOptions,
    classpaths = classpaths,
    processingStepsFactory = processingSteps
  )
}

internal object SqliteMagicCompilation {
  fun compile(
    vararg sources: SourceFile,
    kspOptions: Map<String, String> = emptyMap(),
    classpaths: List<File> = emptyList(),
    processingStepsFactory: ((Environment) -> List<ProcessingStep>)?
  ): ProcessorCompilationResult {
    val provider = RecordingSqliteMagicSymbolProcessorProvider(processingStepsFactory)
    val result = KotlinCompilation()
      .withTestSetup(
        sources = sources,
        classpaths = classpaths
      )
      .apply {
        useKsp2()
        symbolProcessorProviders = mutableListOf(provider)
        kspProcessorOptions = kspOptions.toMutableMap()
      }
      .compile()
    return ProcessorCompilationResult(
      result = result,
      environment = provider.processor.environment
    )
  }
}

private class RecordingSqliteMagicSymbolProcessorProvider(
  private val processingStepsFactory: ((Environment) -> List<ProcessingStep>)?
) : SymbolProcessorProvider {
  lateinit var processor: SqliteMagicSymbolProcessor

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    processor = when {
      processingStepsFactory != null -> SqliteMagicSymbolProcessor(
        symbolProcessorEnvironment = environment,
        processingStepsProvider = processingStepsFactory
      )
      else -> SqliteMagicSymbolProcessor(
        symbolProcessorEnvironment = environment
      )
    }
    environment.registerProcessorForNewFeatures(processor)
    return processor
  }
}
