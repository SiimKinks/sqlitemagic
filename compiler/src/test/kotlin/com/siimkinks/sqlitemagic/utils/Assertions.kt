package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat

private val WHITESPACE = Regex("""\s+""")
private val OPENING_PARENTHESIS_SPACE = Regex("""\( """)
private val CLOSING_PARENTHESIS_SPACE = Regex(""",? \)""")

/**
 * This source with its layout undone: each whitespace run is one space and a call with one argument per line reads as
 * a single-line call, so content assertions do not depend on how generated code breaks lines.
 *
 * Example: `listOf(\n  ID,\n  NAME,\n)` -> `listOf(ID, NAME)`.
 */
fun String.unwrapped() = this
  .replace(WHITESPACE, " ")
  .replace(OPENING_PARENTHESIS_SPACE, "(")
  .replace(CLOSING_PARENTHESIS_SPACE, ")")

fun String.assertContains(
  vararg expected: String
) = apply {
  val subject = assertThat(this)
  expected.forEach(subject::contains)
}

fun String.assertContainsUnwrapped(
  vararg expected: String
) = apply {
  unwrapped().assertContains(*expected)
}

fun String.assertDoesNotContain(
  vararg unexpected: String
) = apply {
  val subject = assertThat(this)
  unexpected.forEach(subject::doesNotContain)
}

fun String.assertContainsInOrder(
  vararg expected: String
) = apply {
  expected.fold(-1) { previousIndex, value ->
    indexOf(
      string = value,
      startIndex = previousIndex + 1
    ).also { assertThat(it).isGreaterThan(previousIndex) }
  }
}

private val NEXT_MEMBER = Regex("""\n  (?:(?:public|internal|private|override) )*(?:fun|val) """)

fun String.functionBody(name: String): String {
  val declaration = "fun $name("
  check(contains(declaration)) { "Missing function $name in:\n$this" }
  val body = substringAfter(declaration)
  return body.take(NEXT_MEMBER.find(body)?.range?.first ?: body.length)
}
