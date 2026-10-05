package com.siimkinks.sqlitemagic.migration

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

object MigrationScriptValidation {
  fun readUtf8(stream: InputStream) = InputStreamReader(
    stream,
    Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
  ).use(InputStreamReader::readText)

  fun validate(
    text: String,
    resourceName: String,
    allowEmpty: Boolean = true
  ) {
    var hasStatement = false
    text.lineSequence().forEachIndexed { index, line ->
      if (line.isNotBlank() && !line.trimStart().startsWith("--")) {
        hasStatement = true
        try {
          validateSqlLine(line)
        } catch (failure: Exception) {
          throw IllegalStateException("Malformed migration script $resourceName at physical line ${index + 1}", failure)
        }
      }
    }
    check(allowEmpty || hasStatement) { "Empty migration script $resourceName" }
  }
}
