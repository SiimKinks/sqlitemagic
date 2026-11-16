package com.siimkinks.sqlitemagic.migration

import java.util.Locale

internal fun validateSqlLine(sql: String) {
  var quote: Char? = null
  var blockComment = false
  var terminated = false
  var parentheses = 0
  var hasToken = false
  val leadingWords = mutableListOf<String>()
  var leadingWordsClosed = false
  var trigger = false
  var afterSemicolon = false
  var endAfterSemicolon = false
  fun otherToken() {
    hasToken = true
    leadingWordsClosed = true
    afterSemicolon = false
    endAfterSemicolon = false
  }
  var index = 0
  while (index < sql.length) {
    val character = sql[index]
    val next = sql.getOrNull(index + 1)
    when {
      blockComment -> {
        if (character == '*' && next == '/') {
          blockComment = false
          index++
        }
      }
      quote != null -> {
        if (character == quote) {
          when {
            quote != ']' && next == quote -> index++
            else -> quote = null
          }
        }
      }
      character == '-' && next == '-' -> break
      character == '/' && next == '*' -> {
        blockComment = true
        index++
      }
      character.isWhitespace() || !hasToken && character == '\uFEFF' -> Unit
      terminated -> error("Each physical line must contain exactly one SQL statement")
      character == ';' -> {
        require(hasToken && parentheses == 0) { "SQL statement is incomplete before its terminating semicolon" }
        when {
          !trigger || endAfterSemicolon -> terminated = true
          else -> {
            leadingWordsClosed = true
            afterSemicolon = true
            endAfterSemicolon = false
          }
        }
      }
      character in "'\"`[" -> {
        quote = when (character) {
          '[' -> ']'
          else -> character
        }
        otherToken()
      }
      character == '(' -> {
        parentheses++
        otherToken()
      }
      character == ')' -> {
        parentheses--
        require(parentheses >= 0) { "Unmatched closing parenthesis in SQL statement" }
        otherToken()
      }
      character.isWordStart() -> {
        if (!hasToken) {
          require(
            !TRANSACTION_END.matchesAt(
              input = sql,
              index = index
            )
          ) { "Migration statement ends the upgrade's transaction, which the migration caller owns" }
        }
        var end = index + 1
        while (end < sql.length && sql[end].isWordPart()) end++
        val word = sql
          .substring(
            startIndex = index,
            endIndex = end
          )
          .uppercase(Locale.ROOT)
        hasToken = true
        if (!leadingWordsClosed && leadingWords.size < TRIGGER_START_WORDS) {
          leadingWords += word
          trigger = trigger || leadingWords.startsTrigger()
        }
        endAfterSemicolon = afterSemicolon && word == "END"
        afterSemicolon = false
        index = end - 1
      }
      else -> otherToken()
    }
    index++
  }
  require(
    hasToken && quote == null && !blockComment && parentheses == 0 && (!trigger || terminated || endAfterSemicolon)
  ) {
    "SQL statement must be complete on one physical line"
  }
}

private fun List<String>.startsTrigger() = firstOrNull() == "CREATE" && when (size) {
  2 -> last() == "TRIGGER"
  3 -> this[1] in TEMPORARY_WORDS && last() == "TRIGGER"
  else -> false
}

private fun Char.isWordStart() = isLetter() || this == '_' || code >= 0x80

private fun Char.isWordPart() = isLetterOrDigit() || this == '_' || this == '$' || code >= 0x80

private const val TRIGGER_START_WORDS = 3
private val TEMPORARY_WORDS = setOf("TEMP", "TEMPORARY")
private const val GAP = """(?:\s|/\*.*?\*/)"""
private val TRANSACTION_END = Regex(
  """(?i)(?:COMMIT|END|ROLLBACK(?!$GAP+(?:TRANSACTION$GAP+)?TO\b))(?![\w$\x{80}-\x{10FFFF}])"""
)
