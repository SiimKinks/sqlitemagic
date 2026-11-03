package com.siimkinks.sqlitemagic.framework

import kotlin.text.RegexOption.DOT_MATCHES_ALL
import kotlin.text.RegexOption.IGNORE_CASE

internal fun hasSchemaDependentColumns(sql: String) = '*' in sql && codeOf(sql).let { code ->
  when {
    RETURNING_CLAUSE.containsMatchIn(code) -> false
    else -> RESULT_STAR
      .findAll(code)
      .any { star ->
        !expandsOnlySubquery(
          code = code,
          starEnd = star.range.last + 1
        )
      }
  }
}

private fun codeOf(sql: String) = QUOTED_OR_COMMENT.replace(sql) { " ".repeat(it.value.length) }

private fun expandsOnlySubquery(code: String, starEnd: Int): Boolean {
  val source = SUBQUERY_SOURCE.matchAt(
    input = code,
    index = starEnd
  ) ?: return false
  val end = closingParenthesis(
    code = code,
    start = source.range.last + 1
  )
  return end >= 0 && SOURCE_END.matchesAt(
    input = code,
    index = end + 1
  )
}

private fun closingParenthesis(code: String, start: Int): Int {
  var depth = 1
  for (index in start..<code.length) {
    when (code[index]) {
      '(' -> depth++
      ')' -> {
        depth--
        if (depth == 0) return index
      }
    }
  }
  return -1
}

private val RESULT_STAR = Regex(
  pattern = """(?:\b(?:SELECT|DISTINCT|ALL)|[,.])\s*\*""",
  option = IGNORE_CASE
)
private val RETURNING_CLAUSE = Regex(
  pattern = """\bRETURNING\b""",
  option = IGNORE_CASE
)
private val SUBQUERY_SOURCE = Regex(
  pattern = """\s*FROM\s*\(\s*(?:SELECT|WITH|VALUES)\b""",
  option = IGNORE_CASE
)
private val QUOTED_OR_COMMENT = Regex(
  pattern = """'[^']*'|"[^"]*"|`[^`]*`|\[[^\]]*]|--[^\n]*|/\*.*?(?:\*/|\z)""",
  option = DOT_MATCHES_ALL
)
private val SOURCE_END = Regex(
  pattern = """\s*(?:\z|[);]|(?:UNION|INTERSECT|EXCEPT|WHERE|GROUP|HAVING|WINDOW|ORDER|LIMIT)\b)""",
  option = IGNORE_CASE
)
