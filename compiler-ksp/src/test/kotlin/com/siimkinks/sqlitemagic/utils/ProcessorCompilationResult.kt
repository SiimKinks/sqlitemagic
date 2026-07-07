package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
data class ProcessorCompilationResult(
  val result: CompilationResult,
  val processor: SqliteMagicSymbolProcessor
) {
  @OptIn(ExperimentalCompilerApi::class)
  fun hasExitCode(expected: ExitCode) = apply {
    result.hasExitCode(expected)
  }

  @OptIn(ExperimentalCompilerApi::class)
  fun isOk() = apply {
    result.isOk()
  }

  @OptIn(ExperimentalCompilerApi::class)
  fun hasMessage(message: String) = apply {
    result.hasMessage(message)
  }
}

@OptIn(ExperimentalCompilerApi::class)
fun CompilationResult.hasExitCode(expected: ExitCode) = apply {
  assertThat(exitCode).isEqualTo(expected)
}

@OptIn(ExperimentalCompilerApi::class)
fun CompilationResult.isOk() = hasExitCode(OK)

@OptIn(ExperimentalCompilerApi::class)
fun CompilationResult.hasMessage(message: String) = apply {
  assertThat(messages).contains(message)
}