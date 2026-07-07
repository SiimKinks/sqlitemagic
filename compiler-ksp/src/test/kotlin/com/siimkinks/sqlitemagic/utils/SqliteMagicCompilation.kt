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
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
internal object SqliteMagicCompilation {
  fun compile(
    vararg sources: SourceFile,
    kspOptions: Map<String, String> = emptyMap(),
    classpaths: List<File> = emptyList(),
    processingStepsFactory: ((Environment) -> List<ProcessingStep>)? = null
  ): ProcessorCompilationResult {
    val provider = RecordingSqliteMagicSymbolProcessorProvider(processingStepsFactory)
    val result = KotlinCompilation()
      .apply {
        useKsp2()
        this.sources = sources.toList()
        inheritClassPath = true
        this.classpaths = classpaths
        symbolProcessorProviders = mutableListOf(provider)
        kspProcessorOptions = kspOptions.toMutableMap()
      }
      .compile()
    return ProcessorCompilationResult(
      result = result,
      processor = provider.processor
    )
  }
}

private class RecordingSqliteMagicSymbolProcessorProvider(
  private val processingStepsFactory: ((Environment) -> List<ProcessingStep>)? = null
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
    return processor
  }
}