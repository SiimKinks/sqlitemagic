package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_MODEL_DAO
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_TABLE_STRUCTURE
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.squareup.kotlinpoet.ClassName

interface ReadModelGenerationNames {
  val packageName: String
  val artifactStem: String

  val daoClassName: ClassName
  val tableClassName: ClassName
}

private class CanonicalReadModelGenerationNames(
  override val packageName: String,
  override val artifactStem: String
) : ReadModelGenerationNames {
  override val daoClassName = ClassName(
    packageName,
    "SqliteMagic_${artifactStem}_$CLASS_MODEL_DAO"
  )
  override val tableClassName = ClassName(
    PACKAGE_ROOT,
    "${artifactStem}$CLASS_TABLE_STRUCTURE"
  )
}

internal fun readModelGenerationNames(
  packageName: String,
  artifactStem: String
): ReadModelGenerationNames = CanonicalReadModelGenerationNames(
  packageName = packageName,
  artifactStem = artifactStem
)
