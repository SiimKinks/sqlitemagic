package com.siimkinks.sqlitemagic.migration

import com.siimkinks.sqlitemagic.internal.SqlUtil.requireNoTransactionControl

/** Checks physical statements and caller-owned transactions; SQLite owns other SQL grammar and semantics. */
internal fun validateSqlLine(sql: String) {
  var quote: Char? = null
  var blockComment = false
  var terminated = false
  var parentheses = 0
  var hasToken = false
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
        terminated = true
      }
      character in "'\"`[" -> {
        quote = when (character) {
          '[' -> ']'
          else -> character
        }
        hasToken = true
      }
      character == '(' -> {
        parentheses++
        hasToken = true
      }
      character == ')' -> {
        parentheses--
        require(parentheses >= 0) { "Unmatched closing parenthesis in SQL statement" }
      }
      else -> {
        if (!hasToken) requireNoTransactionControl(
          sql = sql,
          start = index
        )
        hasToken = true
      }
    }
    index++
  }
  require(hasToken && quote == null && !blockComment && parentheses == 0) {
    "SQL statement must be complete on one physical line"
  }
}
