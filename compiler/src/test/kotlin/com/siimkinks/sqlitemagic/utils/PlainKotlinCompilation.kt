package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File

private const val TEST_JVM_TARGET_PROPERTY = "sqlitemagic.test.jvmTarget"

object PlainKotlinCompilation {
  fun compile(
    vararg sources: SourceFile,
    classpaths: List<File> = emptyList()
  ) = KotlinCompilation()
    .withTestSetup(
      sources = sources,
      classpaths = classpaths
    )
    .compile()

  fun compile(
    fileName: String,
    @Language("kotlin") contents: String
  ) = compile(
    SourceFile.kotlin(
      name = fileName,
      contents = contents
    )
  )
}

internal fun KotlinCompilation.withTestSetup(
  sources: Array<out SourceFile>,
  classpaths: List<File>
) = apply {
  jvmTarget = requireNotNull(System.getProperty(TEST_JVM_TARGET_PROPERTY)) {
    "Run compiler tests via Gradle to supply $TEST_JVM_TARGET_PROPERTY"
  }
  this.sources = sources.toList() + TYPE_WITNESSES
  inheritClassPath = true
  this.classpaths = classpaths
}
