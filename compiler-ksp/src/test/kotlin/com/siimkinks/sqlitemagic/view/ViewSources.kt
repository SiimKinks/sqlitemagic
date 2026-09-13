package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

internal object ViewSources {
  const val FIXTURE_PACKAGE = "$PACKAGE.view"

  fun view(
    name: String,
    @Language("kotlin") body: String,
    packageName: String = FIXTURE_PACKAGE
  ) = SourceFile.kotlin(
    name = "$name.kt",
    contents = viewContents(
      body = body,
      packageName = packageName
    )
  )

  @Language("kotlin")
  internal fun viewContents(
    @Language("kotlin") body: String,
    packageName: String = FIXTURE_PACKAGE
  ) = """
    package $packageName

    import com.siimkinks.sqlitemagic.CompiledSelect
    import com.siimkinks.sqlitemagic.Select.Select1
    import com.siimkinks.sqlitemagic.Select.SelectN
    import com.siimkinks.sqlitemagic.annotation.Column
    import com.siimkinks.sqlitemagic.annotation.Table
    import com.siimkinks.sqlitemagic.annotation.View
    import com.siimkinks.sqlitemagic.annotation.ViewColumn
    import com.siimkinks.sqlitemagic.annotation.ViewQuery

    private fun <T, S> compileOnlySelect(): CompiledSelect<T, S> = error("compile-only")

    $body
  """
}
