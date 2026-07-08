package com.siimkinks.sqlitemagic

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.siimkinks.sqlitemagic.GlobalConst.CLASS_NAME_GENERATED_CLASSES_MANAGER
import com.siimkinks.sqlitemagic.GlobalConst.CLASS_NAME_MAIN_GENERATED_CLASSES_MANAGER
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_NAME
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_VERSION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.utils.firstCharToUpperCase

class Environment(symbolProcessorEnvironment: SymbolProcessorEnvironment) {
  val codeGenerator = symbolProcessorEnvironment.codeGenerator
  val logger = symbolProcessorEnvironment.logger
  val options = CompilerOptions.from(symbolProcessorEnvironment.options)

  var dbMetadata = DatabaseMetadata(dbName = options.dbName, dbVersion = options.dbVersion)
    private set

  var submoduleName: String? = null
    private set
  var submoduleDatabases: List<SubmoduleDatabaseMetadata>? = null

  var processingRounds = 0
    private set

  var isProcessingFailed = false

  val isSubmodule get() = !submoduleName.isNullOrEmpty()

  val hasSubmodules get() = !submoduleDatabases.isNullOrEmpty()

  fun incrementRound() {
    processingRounds++
  }

  fun setDatabaseMetadata(
    dbName: String,
    dbVersion: Int
  ) {
    dbMetadata = dbMetadata.copy(
      dbName = dbName
        .takeIf(String::isNotEmpty)
        ?: dbMetadata.dbName,
      dbVersion = dbVersion
        .takeIf { it >= 0 && !options.isDebugVariant }
        ?: dbMetadata.dbVersion
    )
  }

  fun setSubmoduleName(name: String) {
    submoduleName = name.firstCharToUpperCase()
  }

  fun getGenClassesManagerClassName(
    moduleName: String? = submoduleName
  ) = when {
    moduleName.isNullOrEmpty() -> CLASS_NAME_MAIN_GENERATED_CLASSES_MANAGER
    else -> moduleName + CLASS_NAME_GENERATED_CLASSES_MANAGER
  }
}

@ConsistentCopyVisibility
data class CompilerOptions private constructor(
  val debug: Boolean,
  val isDebugVariant: Boolean,
  val dbName: String?,
  val dbVersion: Int?
) {
  companion object {
    fun from(options: Map<String, String>) = CompilerOptions(
      debug = options[OPTION_DEBUG].toBoolean(),
      isDebugVariant = options[OPTION_VARIANT_DEBUG].toBoolean(),
      dbName = options[OPTION_DB_NAME].takeUnless(String?::isNullOrEmpty),
      dbVersion = options[OPTION_DB_VERSION]
        ?.takeIf(String::isNotEmpty)
        ?.toIntOrNull()
    )
  }
}
