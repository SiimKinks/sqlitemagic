package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_MODEL_DAO
import com.siimkinks.sqlitemagic.GeneratedNames.CLASS_TABLE_STRUCTURE
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.utils.generatedFieldName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

internal data class GenerationNames(
  val artifactStem: String
) {
  val daoClassName = ClassName(PACKAGE_ROOT, "SqliteMagic_${artifactStem}_$CLASS_MODEL_DAO")
  val tableClassName = ClassName(PACKAGE_ROOT, "$artifactStem$CLASS_TABLE_STRUCTURE")
  val structureFieldName = artifactStem.generatedFieldName()
  val adapterClassName = ClassName(PACKAGE_ROOT, "SqliteMagic_${artifactStem}_Adapter")
  val extensionsFileName = "_$artifactStem"
  val bulkOperationsObjectName = "${artifactStem}s"
  val tableHandle = CodeBlock.of("%T.%N", tableClassName, structureFieldName)
}
