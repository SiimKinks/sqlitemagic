package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK

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
}

fun CompilationResult.hasExitCode(expected: ExitCode) = apply {
  assertThat(exitCode).isEqualTo(expected)
}

fun CompilationResult.isOk() = hasExitCode(OK)

fun CompilationResult.hasMessage(message: String) = apply {
  assertThat(messages).contains(message)
}
