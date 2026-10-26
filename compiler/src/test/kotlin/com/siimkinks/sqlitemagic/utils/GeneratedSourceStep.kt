package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import org.intellij.lang.annotations.Language

internal class GeneratedSourceStep(
  private val environment: Environment,
  private val fileName: String,
  @Language("kotlin")
  private val contents: String,
  private val packageName: String = PACKAGE,
  private val dependencies: (Resolver) -> Dependencies = { Dependencies(aggregating = false) },
  private val isReady: () -> Boolean = { true }
) : ProcessingStep {
  private var generated = false

  override fun process(resolver: Resolver) = when {
    generated || !isReady() -> Continue
    else -> {
      generated = true
      environment.codeGenerator
        .createNewFile(
          dependencies = dependencies(resolver),
          packageName = packageName,
          fileName = fileName
        )
        .bufferedWriter()
        .use { it.write(contents.trimIndent()) }
      Continue
    }
  }
}
