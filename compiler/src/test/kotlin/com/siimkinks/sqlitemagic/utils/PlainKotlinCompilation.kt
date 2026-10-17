package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.File

private const val TEST_JVM_TARGET_PROPERTY = "sqlitemagic.test.jvmTarget"

object PlainKotlinCompilation {
  fun compile(
    vararg sources: SourceFile,
    classpaths: List<File> = emptyList()
  ) = KotlinCompilation()
    .apply {
      jvmTarget = requireNotNull(System.getProperty(TEST_JVM_TARGET_PROPERTY)) {
        "Run compiler tests via Gradle to supply $TEST_JVM_TARGET_PROPERTY"
      }
      this.sources = sources.toList()
      inheritClassPath = true
      this.classpaths = classpaths
    }
    .compile()
}
