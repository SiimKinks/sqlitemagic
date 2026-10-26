package com.siimkinks.sqlitemagic.utils

import com.tschuchort.compiletesting.SourceFile

object SqliteMagicSources {
  const val PACKAGE = "com.example.sqlitemagic"

  fun readDriverSupport() = SourceFile.kotlin(
    name = "ReadDriverSupport.kt",
    contents = """
      @file:OptIn(InternalSqliteMagicApi::class)

      package fixtures.driver

      import android.database.Cursor
      import com.siimkinks.sqlitemagic.Table
      import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
      import com.siimkinks.sqlitemagic.internal.RelatedEntities
      import java.lang.reflect.Proxy

      fun cursor(vararg values: Any?) = Proxy.newProxyInstance(
        Cursor::class.java.classLoader,
        arrayOf(Cursor::class.java)
      ) { _, method, arguments ->
        val value = values[arguments[0] as Int]
        when (method.name) {
          "isNull" -> value == null
          "getLong" -> value as Long
          "getInt" -> (value as Long).toInt()
          "getString" -> value
          else -> error("Unexpected cursor method: " + method.name)
        }
      } as Cursor

      fun unjoined(table: Table<*>) = RelatedEntities::class.java
        .declaredConstructors
        .single { it.parameterCount == 6 }
        .newInstance(
          table,
          arrayOfNulls<RelatedEntities>(1),
          0,
          0,
          IntArray(0),
          emptyList<RelatedEntities>()
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
