package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SqliteMagicSymbolProcessorTest {
  @Test
  fun `compiles sources annotated with supported annotations`() {
    val result = compileTestTable()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  @Test
  fun `emits debug message when debug option is enabled`() {
    val result = compileTestTable(
      kspOptions = mapOf(SqliteMagicSymbolProcessor.OPTION_DEBUG to "true")
    )

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    assertThat(result.messages).contains("SqliteMagic KSP debug mode enabled")
  }

  private fun compileTestTable(
    kspOptions: Map<String, String> = emptyMap()
  ) = KotlinCompilation()
    .apply {
      useKsp2()
      sources = listOf(testTableSource())
      inheritClassPath = true
      symbolProcessorProviders = mutableListOf(
        SqliteMagicSymbolProcessorProvider()
      )
      kspProcessorOptions = kspOptions.toMutableMap()
    }
    .compile()

  private fun testTableSource() = SourceFile.kotlin(
    "TestTable.kt",
    """
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
      """.trimIndent()
  )
}
