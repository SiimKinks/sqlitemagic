package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.SourceFile

object SqliteMagicSources {
  const val PACKAGE = "com.example.sqlitemagic"

  fun testTable() = SourceFile.kotlin(
    name = "TestTable.kt",
    contents = """
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
      """
  )
}
