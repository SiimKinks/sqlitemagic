package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

internal class GenClassesManagerWriterTest {
  @Test
  fun `writes an aggregating output without depending on all files`() {
    val codeGenerator = RecordingCodeGenerator()

    GenClassesManagerWriter(codeGenerator)
      .write(
        database = mockGeneratedDatabaseElement(),
        orderedTables = CreationOrderedTables.from(emptyList())
      )

    assertThat(codeGenerator.dependencies.aggregating).isTrue()
    assertThat(codeGenerator.dependencies.isAllSources).isFalse()
    assertThat(codeGenerator.dependencies.originatingFiles).isEmpty()
  }
}

private class RecordingCodeGenerator : CodeGenerator {
  lateinit var dependencies: Dependencies

  override val generatedFile: Collection<File> = emptyList()

  override fun createNewFile(
    dependencies: Dependencies,
    packageName: String,
    fileName: String,
    extensionName: String
  ): OutputStream {
    this.dependencies = dependencies
    return ByteArrayOutputStream()
  }

  override fun createNewFileByPath(
    dependencies: Dependencies,
    path: String,
    extensionName: String
  ): OutputStream = error("Not used")

  override fun associate(
    sources: List<KSFile>,
    packageName: String,
    fileName: String,
    extensionName: String
  ) = Unit

  override fun associateByPath(
    sources: List<KSFile>,
    path: String,
    extensionName: String
  ) = Unit

  override fun associateWithClasses(
    classes: List<KSClassDeclaration>,
    packageName: String,
    fileName: String,
    extensionName: String
  ) = Unit
}
