package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.SourceFile

object SqliteMagicSources {
  const val PACKAGE = "com.example.sqlitemagic"

  fun mainDatabaseWithSubmodule() = SourceFile.kotlin(
    name = "MainDatabase.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(submodules = [FeatureDatabase::class])
      class MainDatabase
    """
  )

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
