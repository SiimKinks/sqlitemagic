package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.Environment

internal class ModelCollectionReporter(
  private val environment: Environment
) {
  var errorCount = 0
    private set

  val hasErrors get() = errorCount > 0

  fun error(
    message: String,
    symbol: KSAnnotated?
  ): Boolean {
    errorCount++
    environment.logger.error(
      message = message,
      symbol = symbol
    )
    return false
  }

  fun <T> takeIfNoNewErrors(predicate: () -> T): T? {
    val currentErrorCount = errorCount
    return predicate().takeIf { errorCount == currentErrorCount }
  }
}
