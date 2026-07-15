package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.intellij.lang.annotations.Language
import java.io.File

data class ProcessorCompilationResult(
  val result: CompilationResult,
  val processor: SqliteMagicSymbolProcessor
) {
  val environment get() = processor.environment
  fun hasExitCode(expected: ExitCode) = apply {
    result.hasExitCode(expected)
  }

  fun isOk() = apply {
    result.isOk()
  }

  fun hasMessage(message: String) = apply {
    result.hasMessage(message)
  }

  fun assertCompilationError(
    vararg messages: String
  ) = apply {
    hasExitCode(COMPILATION_ERROR)
    messages.forEach(this::hasMessage)
  }

  fun assertGeneratedSource(
    fileName: String,
    @Language("kotlin") expectedSource: String
  ) = withGeneratedSource(fileName) { generatedSource ->
    assertThat(generatedSource).isEqualTo(expectedSource)
  }

  fun withGeneratedSource(
    fileName: String,
    assert: (String) -> Unit
  ) = apply {
    assert(generatedSource(fileName))
  }

  fun generatedSource(fileName: String) =
    (result as JvmCompilationResult)
      .sourcesGeneratedBySymbolProcessor
      .single { it.name == fileName }
      .readText()

  fun generatedSourceNames() =
    (result as JvmCompilationResult)
      .sourcesGeneratedBySymbolProcessor
      .map(File::getName)
      .toList()
}

fun CompilationResult.hasExitCode(expected: ExitCode) = apply {
  assertThat(exitCode).isEqualTo(expected)
}

fun CompilationResult.isOk() = hasExitCode(OK)

fun CompilationResult.hasMessage(message: String) = apply {
  assertThat(messages).contains(message)
}
