package com.siimkinks.sqlitemagic

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_NAME
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_VERSION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG

class Environment(symbolProcessorEnvironment: SymbolProcessorEnvironment) {
  val codeGenerator = symbolProcessorEnvironment.codeGenerator
  val logger = symbolProcessorEnvironment.logger
  val options = CompilerOptions.from(symbolProcessorEnvironment.options)

  var processingRounds = 0
    private set

  var isProcessingFailed = false

  fun incrementRound() {
    processingRounds++
  }
}

@ConsistentCopyVisibility
data class CompilerOptions private constructor(
  val debug: Boolean,
  val debugVariant: Boolean,
  val dbName: String?,
  val dbVersion: Int?
) {
  companion object {
    fun from(options: Map<String, String>) = CompilerOptions(
      debug = options[OPTION_DEBUG].toBoolean(),
      debugVariant = options[OPTION_VARIANT_DEBUG].toBoolean(),
      dbName = options[OPTION_DB_NAME].takeUnless(String?::isNullOrEmpty),
      dbVersion = options[OPTION_DB_VERSION]
        ?.takeIf(String::isNotEmpty)
        ?.toIntOrNull()
    )
  }
}
