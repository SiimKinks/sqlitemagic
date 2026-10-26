package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.Environment
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import java.io.File

interface ProcessorOutput {
  val exitCode: ExitCode
  val messages: String

  fun generatedSourceNames(): Collection<String>

  fun generatedSource(fileName: String): String
}

fun <T : ProcessorOutput> T.isOk() = hasExitCode(OK)

fun <T : ProcessorOutput> T.hasMessage(message: String) = apply {
  assertThat(messages).contains(message)
}

fun <T : ProcessorOutput> T.assertCompilationError(vararg expected: String) = apply {
  hasExitCode(COMPILATION_ERROR)
  expected.forEach(this::hasMessage)
}

fun <T : ProcessorOutput> T.assertGeneratedSources(vararg fileNames: String) = apply {
  assertThat(generatedSourceNames()).containsAtLeastElementsIn(fileNames.asList())
}

fun <T : ProcessorOutput> T.assertNotGeneratedSources(vararg fileNames: String) = apply {
  assertThat(generatedSourceNames()).containsNoneIn(fileNames.asList())
}

fun <T : ProcessorOutput> T.withGeneratedSource(
  fileName: String,
  assertion: (String) -> Unit
) = apply {
  assertion(generatedSource(fileName))
}

fun <T : ProcessorOutput> T.assertNoWarnings(vararg fileNames: String) = apply {
  assertWithMessage(messages)
    .that(
      messages
        .lines()
        .filter { line -> line.startsWith("w: ") && fileNames.any { "/$it:" in line } }
    )
    .isEmpty()
}

private fun <T : ProcessorOutput> T.hasExitCode(expected: ExitCode) = apply {
  assertExitCode(
    actual = exitCode,
    expected = expected,
    messages = messages
  )
}

internal class ProcessorCompilationResult(
  private val result: JvmCompilationResult,
  val environment: Environment
) : ProcessorOutput {
  override val exitCode get() = result.exitCode
  override val messages get() = result.messages
  val outputDirectory: File get() = result.outputDirectory

  override fun generatedSource(fileName: String) = result
    .sourcesGeneratedBySymbolProcessor
    .single { it.name == fileName }
    .readText()

  override fun generatedSourceNames() = result
    .sourcesGeneratedBySymbolProcessor
    .map(File::getName)
    .toList()

  fun loadClass(className: String): Class<*> = result.classLoader.loadClass(className)

  fun generatedSources() = GeneratedSources(
    exitCode = exitCode,
    messages = messages,
    sources = result
      .sourcesGeneratedBySymbolProcessor
      .associate { it.name to it.readText() }
  )
}

/** Output of a compilation that tests share, without compiler and processor state that would stay reachable. */
class GeneratedSources(
  override val exitCode: ExitCode,
  override val messages: String,
  private val sources: Map<String, String>
) : ProcessorOutput {
  override fun generatedSourceNames() = sources.keys

  override fun generatedSource(fileName: String) = sources.getValue(fileName)
}

fun CompilationResult.isOk() = hasExitCode(OK)

fun CompilationResult.assertCompilationError(vararg expected: String) = apply {
  hasExitCode(COMPILATION_ERROR)
  expected.forEach(this::hasMessage)
}

private fun CompilationResult.hasExitCode(expected: ExitCode) = apply {
  assertExitCode(
    actual = exitCode,
    expected = expected,
    messages = messages
  )
}

private fun CompilationResult.hasMessage(message: String) = apply {
  assertThat(messages).contains(message)
}

private fun assertExitCode(
  actual: ExitCode,
  expected: ExitCode,
  messages: String
) {
  assertWithMessage(messages)
    .that(actual)
    .isEqualTo(expected)
}
