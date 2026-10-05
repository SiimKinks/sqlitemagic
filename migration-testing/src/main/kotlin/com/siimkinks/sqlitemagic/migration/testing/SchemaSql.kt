package com.siimkinks.sqlitemagic.migration.testing

internal class SchemaInspectionException(message: String) : IllegalStateException(message)

internal data class SqlToken(
  val text: String,
  val quotedIdentifier: Boolean = false,
  val literal: Boolean = false,
  val doubleQuoted: Boolean = false
) {
  val word get() = text.sqliteIdentifier()
}

/** Lexes single schema definitions; quoted contents and numeric spelling are never case folded. */
internal object SchemaSql {
  fun tokens(sql: String): List<SqlToken> {
    val result = mutableListOf<SqlToken>()
    var index = 0
    while (index < sql.length) {
      val start = index
      val character = sql[index]
      when {
        character.isWhitespace() -> index++
        sql.startsWith(
          prefix = "--",
          startIndex = index
        ) -> index = sql
          .indexOf(
            char = '\n',
            startIndex = index
          )
          .takeIf { it >= 0 } ?: sql.length
        sql.startsWith(
          prefix = "/*",
          startIndex = index
        ) -> {
          val end = sql.indexOf(
            string = "*/",
            startIndex = index + 2
          )
          requireInspection(
            condition = end >= 0,
            message = "Unterminated SQL comment"
          )
          index = end + 2
        }
        character in "'\"`[" -> {
          val closing = when (character) {
            '[' -> ']'
            else -> character
          }
          index++
          val value = StringBuilder()
          var closed = false
          while (index < sql.length) {
            val next = sql[index++]
            when {
              next != closing -> value.append(next)
              closing != ']' && index < sql.length && sql[index] == closing -> {
                value.append(closing)
                index++
              }
              else -> {
                closed = true
                break
              }
            }
          }
          requireInspection(
            condition = closed,
            message = "Unterminated quoted SQL token"
          )
          result += when (character) {
            '\'' -> SqlToken(
              text = sql.substring(
                startIndex = start,
                endIndex = index
              ),
              literal = true
            )
            else -> SqlToken(
              text = value.toString(),
              quotedIdentifier = true,
              doubleQuoted = character == '"'
            )
          }
        }
        character.isLetter() || character == '_' || character.code >= 128 -> {
          index++
          while (index < sql.length && (sql[index].isLetterOrDigit() || sql[index] in "_$")) index++
          result += SqlToken(
            sql.substring(
              startIndex = start,
              endIndex = index
            )
          )
        }
        character.isDigit() || character == '.' && index + 1 < sql.length && sql[index + 1].isDigit() -> {
          index++
          while (index < sql.length && (sql[index].isLetterOrDigit() || sql[index] == '.')) index++
          if (index < sql.length && sql[index] in "+-" && sql[index - 1] in "eE") {
            index++
            while (index < sql.length && sql[index].isDigit()) index++
          }
          result += SqlToken(
            text = sql.substring(
              startIndex = start,
              endIndex = index
            ),
            literal = true
          )
        }
        character in "(),.;+-*/%=<>!|&~" -> {
          index++
          if (index < sql.length && sql.substring(
              startIndex = start,
              endIndex = index + 1
            ) in operators
          ) index++
          if (index < sql.length && sql.substring(
              startIndex = start,
              endIndex = index + 1
            ) == "->>"
          ) index++
          result += SqlToken(
            sql.substring(
              startIndex = start,
              endIndex = index
            )
          )
        }
        else -> throw SchemaInspectionException("Unsupported schema SQL token '$character' at offset $index")
      }
    }
    return result.dropLastWhile { it.isPunctuation(";") }
  }

  fun canonical(
    tokens: List<SqlToken>,
    identifiers: Set<String>,
    forcedIdentifiers: Set<Int> = emptySet(),
    preserveQuotedValues: Boolean = false
  ): String = tokens.mapIndexed { index, token ->
    val kind = when {
      token.literal -> "literal"
      preserveQuotedValues && token.quotedIdentifier -> "quoted"
      token.doubleQuoted && token.word !in identifiers && index !in forcedIdentifiers -> "quoted"
      else -> "atom"
    }
    val value = when (kind) {
      "atom" -> token.word
      else -> token.text
    }
    "$kind:\"${value.escapeToken()}\""
  }
    .joinToString(separator = " ")

  private fun String.escapeToken() = replace(
    oldValue = "\\",
    newValue = "\\\\"
  )
    .replace(
      oldValue = "\"",
      newValue = "\\\""
    )
    .replace(
      oldValue = "\n",
      newValue = "\\n"
    )
    .replace(
      oldValue = "\r",
      newValue = "\\r"
    )
    .replace(
      oldValue = "\t",
      newValue = "\\t"
    )

  fun tableClauses(
    sql: String,
    identifiers: Set<String>
  ): List<String> {
    val tokens = tokens(sql)
    requireInspection(
      condition = tokens.take(2)
        .map(SqlToken::word) == listOf("create", "table"),
      message = "Unsupported table definition"
    )
    val opening = tokens.indexOfFirst { it.isPunctuation("(") }
    requireInspection(
      condition = opening >= 0,
      message = "Table definition has no column list"
    )
    val closing = closing(
      tokens = tokens,
      opening = opening
    )
    val suffix = tokens.drop(closing + 1)
      .map(SqlToken::word)
    requireInspection(
      condition = suffix.all { it in setOf("without", "rowid", "strict", ",") },
      message = "Unsupported table options: $suffix"
    )
    return split(
      tokens.subList(
        fromIndex = opening + 1,
        toIndex = closing
      )
    )
      .map { clause ->
        val constraint = clause.indexOfFirst { !it.quotedIdentifier && it.word in columnConstraints }
          .takeIf { it >= 0 } ?: clause.size
        val typePositions = when {
          !clause.first().quotedIdentifier && clause.first().word in tableConstraints -> emptySet()
          else -> (1 until constraint).toSet()
        }
        canonical(
          tokens = clause,
          identifiers = identifiers,
          forcedIdentifiers = explicitIdentifierPositions(clause) + typePositions
        )
      }
  }

  fun indexDefinition(
    sql: String,
    identifiers: Set<String>
  ): String {
    val tokens = tokens(sql)
    requireInspection(
      condition = tokens.firstOrNull()?.word == "create",
      message = "Unsupported index definition"
    )
    val on = tokens.indexOfFirst { !it.quotedIdentifier && it.word == "on" }
    requireInspection(
      condition = on >= 0,
      message = "Index definition has no ON clause"
    )
    val opening = tokens.indexOfFirst { it.isPunctuation("(") }
    requireInspection(
      condition = opening > on,
      message = "Index definition has no indexed terms"
    )
    closing(
      tokens = tokens,
      opening = opening
    )
    val terms = tokens.drop(opening)
    return canonical(
      tokens = terms,
      identifiers = identifiers,
      forcedIdentifiers = explicitIdentifierPositions(terms)
    )
  }

  private fun explicitIdentifierPositions(tokens: List<SqlToken>): Set<Int> {
    val positions = tokens.withIndex()
      .filter { (_, token) -> !token.quotedIdentifier && token.word in identifierIntroducers }
      .map { it.index + 1 }
      .toMutableSet()
    tokens.forEachIndexed { index, token ->
      if (tokens.getOrNull(index + 1)?.isPunctuation(".") == true) positions += index
      if (!token.quotedIdentifier && token.word == "references" &&
        tokens.getOrNull(index + 2)?.isPunctuation("(") == true
      ) {
        val end = closing(
          tokens = tokens,
          opening = index + 2
        )
        positions += index + 3 until end
      }
    }
    return positions
  }

  private fun split(tokens: List<SqlToken>): List<List<SqlToken>> {
    val parts = mutableListOf<List<SqlToken>>()
    var depth = 0
    var start = 0
    tokens.forEachIndexed { index, token ->
      when (token.text.takeUnless { token.quotedIdentifier || token.literal }) {
        "(" -> depth++
        ")" -> depth--
        "," -> if (depth == 0) {
          parts += tokens.subList(
            fromIndex = start,
            toIndex = index
          )
          start = index + 1
        }
      }
      requireInspection(
        condition = depth >= 0,
        message = "Unbalanced schema expression"
      )
    }
    requireInspection(
      condition = depth == 0,
      message = "Unbalanced schema expression"
    )
    parts += tokens.drop(start)
    requireInspection(
      condition = parts.all(List<SqlToken>::isNotEmpty),
      message = "Empty schema clause"
    )
    return parts
  }

  private fun closing(
    tokens: List<SqlToken>,
    opening: Int
  ): Int {
    var depth = 0
    for (index in opening until tokens.size) {
      when (tokens[index].text.takeUnless { tokens[index].quotedIdentifier || tokens[index].literal }) {
        "(" -> depth++
        ")" -> if (--depth == 0) return index
      }
    }
    throw SchemaInspectionException("Unbalanced schema definition")
  }

  private val operators = setOf("<=", ">=", "<>", "!=", "==", "||", "<<", ">>", "->")
  private val columnConstraints = setOf(
    "primary", "not", "unique", "check", "default", "collate", "references", "generated", "as", "constraint"
  )
  private val tableConstraints = setOf("constraint", "primary", "unique", "check", "foreign")
  private val identifierIntroducers = setOf("collate", "constraint", "references", "as")
}

internal fun SqlToken.isPunctuation(value: String) = !quotedIdentifier && !literal && text == value

internal fun String.sqliteIdentifier() = buildString(capacity = length) {
  for (character in this@sqliteIdentifier) {
    append(
      when (character) {
        in 'A'..'Z' -> character + ('a' - 'A')
        else -> character
      }
    )
  }
}

internal fun requireInspection(
  condition: Boolean,
  message: String
) {
  if (!condition) throw SchemaInspectionException(message)
}
