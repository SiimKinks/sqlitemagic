package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

internal class StringUtilTest {
  //region firstCharToUpperCase
  @Test
  fun `uppercases only a lowercase first character`() {
    assertTransformations(
      String::firstCharToUpperCase,
      "hello" mapsTo "Hello",
      "hELLO" mapsTo "HELLO",
      "äpfel" mapsTo "Äpfel"
    )
  }

  @Test
  fun `leaves empty strings and non-lowercase first characters unchanged`() {
    assertTransformations(
      String::firstCharToUpperCase,
      "" mapsTo "",
      "A" mapsTo "A",
      "AlreadyUppercase" mapsTo "AlreadyUppercase",
      "1value" mapsTo "1value",
      "_value" mapsTo "_value"
    )
  }
  //endregion

  //region camelCaseToSnakeCase
  @Test
  fun `converts camel case to snake case`() {
    assertTransformations(
      String::camelCaseToSnakeCase,
      "camelCase" mapsTo "camel_case",
      "multipleWordValue" mapsTo "multiple_word_value",
      "value2D" mapsTo "value2_d",
      "tähtÄ" mapsTo "täht_ä"
    )
  }

  @Test
  fun `handles leading and consecutive uppercase characters`() {
    assertTransformations(
      String::camelCaseToSnakeCase,
      "CamelCase" mapsTo "Camel_case",
      "URLValue" mapsTo "U_r_l_value",
      "aBC" mapsTo "a_b_c"
    )
  }

  @Test
  fun `leaves empty single lowercase and already separated strings unchanged`() {
    assertTransformations(
      String::camelCaseToSnakeCase,
      "" mapsTo "",
      "a" mapsTo "a",
      "lowercase" mapsTo "lowercase",
      "already_snake_case" mapsTo "already_snake_case",
      "value-with spaces" mapsTo "value-with spaces"
    )
  }
  //endregion
}

private data class StringTransformationCase(
  val input: String,
  val expected: String
)

private infix fun String.mapsTo(expected: String) = StringTransformationCase(
  input = this,
  expected = expected
)

private fun assertTransformations(
  transform: (String) -> String,
  vararg cases: StringTransformationCase
) {
  cases.forEach { case ->
    assertWithMessage("transformation of '${case.input}'")
      .that(transform(case.input))
      .isEqualTo(case.expected)
  }
}
