package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE

fun mockViewGenerationNames(
  packageName: String = PACKAGE,
  artifactStem: String = "TestView"
) = ViewGenerationNames(
  packageName = packageName,
  artifactStem = artifactStem
)
