package com.siimkinks.sqlitemagic.migration

import com.siimkinks.sqlitemagic.migration.testing.SchemaInspectionException
import com.siimkinks.sqlitemagic.migration.testing.SchemaSql
import com.siimkinks.sqlitemagic.migration.testing.SqlToken
import com.siimkinks.sqlitemagic.migration.testing.sqliteIdentifier

object SchemaSqlComparison {
  fun key(
    sql: String,
    identifiers: Map<String, String> = emptyMap()
  ): String {
    val tokens = try {
      SchemaSql.tokens(sql)
    } catch (_: SchemaInspectionException) {
      return sql
    }
    val replacements = identifiers.entries.associate { (identifier, replacement) ->
      identifier.sqliteIdentifier() to identifierKey(replacement)
    }
    return tokens.joinToString(separator = " ") { token ->
      when {
        token.literal || !token.quotedIdentifier && !token.isWord -> token.text
        else -> replacements[token.word] ?: identifierKey(token.text)
      }
    }
  }

  fun sameStatements(
    first: Collection<String>,
    second: Collection<String>
  ) = first.keys() == second.keys()

  private fun Collection<String>.keys() = mapTo(
    destination = hashSetOf(),
    transform = ::key
  )

  private val SqlToken.isWord get() = text.first().let { it.isLetter() || it == '_' || it.code >= 128 }

  private fun identifierKey(identifier: String) = when {
    identifier.isBareIdentifier() -> identifier
    else -> """"${identifier.replace(oldValue = "\"", newValue = "\"\"")}""""
  }

  private fun String.isBareIdentifier() = isNotEmpty() &&
      (first().isLetter() || first() == '_') &&
      all { it.isLetterOrDigit() || it == '_' || it == '$' }
}
