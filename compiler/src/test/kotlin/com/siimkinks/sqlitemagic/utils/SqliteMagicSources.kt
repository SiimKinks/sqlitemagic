package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.SourceFile

internal object SqliteMagicSources {
  const val PACKAGE = "com.example.sqlitemagic"

  fun readDriverSupport() = SourceFile.kotlin(
    name = "ReadDriverSupport.kt",
    contents = """
      @file:OptIn(InternalSqliteMagicApi::class)

      package fixtures.driver

      import androidx.sqlite.SQLiteStatement
      import com.siimkinks.sqlitemagic.Table
      import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
      import com.siimkinks.sqlitemagic.internal.RelatedEntities
      import java.lang.reflect.Proxy

      fun row(vararg values: Any?) = Proxy.newProxyInstance(
        SQLiteStatement::class.java.classLoader,
        arrayOf(SQLiteStatement::class.java)
      ) { _, method, arguments ->
        fun value() = values[arguments[0] as Int]
        when (method.name) {
          "isNull" -> value() == null
          "getLong" -> value() ?: 0L
          "getInt" -> (value() as Long? ?: 0L).toInt()
          "getDouble" -> value() ?: 0.0
          "getFloat" -> (value() as Double? ?: 0.0).toFloat()
          "getBoolean" -> (value() as Long? ?: 0L) != 0L
          "getText" -> value() ?: ""
          "getBlob" -> value() ?: ByteArray(0)
          else -> error("Unexpected statement method: " + method.name)
        }
      } as SQLiteStatement

      fun unjoined(table: Table<*>) = RelatedEntities::class.java
        .declaredConstructors
        .single { it.parameterCount == 7 }
        .newInstance(
          table,
          arrayOfNulls<RelatedEntities>(1),
          0,
          0,
          IntArray(0),
          emptyList<RelatedEntities>(),
          -1
        ) as RelatedEntities
    """
  )

  fun mainDatabaseWithSubmodule() = SourceFile.kotlin(
    name = "MainDatabase.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(submodules = [FeatureDatabase::class])
      class MainDatabase
    """
  )
}
