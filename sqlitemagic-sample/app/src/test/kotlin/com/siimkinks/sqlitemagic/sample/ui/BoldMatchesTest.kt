package com.siimkinks.sqlitemagic.sample.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

internal class BoldMatchesTest {
  @ParameterizedTest
  @MethodSource("cases")
  fun `bolds the marked matches of the stored title`(
    title: String,
    highlighted: String,
    expected: AnnotatedString
  ) {
    assertEquals(
      expected,
      boldMatches(
        title = title,
        highlighted = highlighted
      )
    )
  }

  companion object {
    private val bold = SpanStyle(fontWeight = FontWeight.Bold)

    @JvmStatic
    fun cases() = listOf(
      argumentSet(
        "one match",
        "Buy oat milk",
        "Buy oat \u0002milk\u0003",
        buildAnnotatedString {
          append("Buy oat ")
          withStyle(bold) { append("milk") }
        }
      ),
      argumentSet(
        "several matches",
        "Buy milk, more milk",
        "Buy \u0002milk\u0003, more \u0002milk\u0003",
        buildAnnotatedString {
          append("Buy ")
          withStyle(bold) { append("milk") }
          append(", more ")
          withStyle(bold) { append("milk") }
        }
      ),
      argumentSet(
        "no match",
        "Buy oat milk",
        "Buy oat milk",
        AnnotatedString("Buy oat milk")
      ),
      argumentSet(
        "start marker in the title",
        "a\u0002b c",
        "a\u0002b \u0002c\u0003",
        buildAnnotatedString {
          append("a\u0002b ")
          withStyle(bold) { append("c") }
        }
      ),
      argumentSet(
        "start marker before the matched word",
        "\u0002x",
        "\u0002\u0002x\u0003",
        buildAnnotatedString {
          append("\u0002")
          withStyle(bold) { append("x") }
        }
      ),
      argumentSet(
        "end marker in the title",
        "a\u0003 b",
        "a\u0003 \u0002b\u0003",
        buildAnnotatedString {
          append("a\u0003 ")
          withStyle(bold) { append("b") }
        }
      )
    )
  }
}
