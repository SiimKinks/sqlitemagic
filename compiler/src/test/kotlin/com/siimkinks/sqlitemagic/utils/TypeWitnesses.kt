package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.SourceFile

internal val TYPE_WITNESSES = SourceFile.kotlin(
  name = "TypeWitnesses.kt",
  contents = """
    package typewitness

    class Exact<T>

    fun <T> T.exactType() = Exact<T>()
  """.trimIndent()
)
