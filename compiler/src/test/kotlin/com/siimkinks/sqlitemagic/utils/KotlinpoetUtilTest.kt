package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertWithMessage
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import org.junit.jupiter.api.Test

internal class KotlinpoetUtilTest {
  private data class Case(
    val label: String,
    val arguments: List<CodeBlock>,
    val expectedCode: String
  )

  @Test
  fun `calls put each argument on its own line only when they have several`() {
    val cases = listOf(
      Case(
        label = "no arguments",
        arguments = emptyList(),
        expectedCode = "Model()"
      ),
      Case(
        label = "one argument stays inline",
        arguments = listOf(namedArgument("name", "%S", "id")),
        expectedCode = """Model(name = "id")"""
      ),
      Case(
        label = "several arguments take one line each with trailing commas",
        arguments = listOf(
          namedArgument("table", "this"),
          namedArgument("name", "%S", "id")
        ),
        expectedCode = """
          Model(
            table = this,
            name = "id",
          )
        """.trimIndent()
      ),
      Case(
        label = "a nested call indents within its argument",
        arguments = listOf(
          namedArgument("id", "1"),
          namedArgument(
            "address",
            "%L",
            callCode(
              callee = CodeBlock.of("Address"),
              arguments = listOf(
                namedArgument("street", "%S", "Main"),
                namedArgument("zip", "%L", 2)
              )
            )
          )
        ),
        expectedCode = """
          Model(
            id = 1,
            address = Address(
              street = "Main",
              zip = 2,
            ),
          )
        """.trimIndent()
      ),
      Case(
        label = "a control-flow expression keeps the comma on its closing brace",
        arguments = listOf(
          namedArgument("id", "1"),
          namedArgument(
            "name",
            "%L",
            buildCodeBlock {
              beginControlFlow("if (absent)")
              add("null\n")
              nextControlFlow("else")
              add("%S\n", "Ada")
              endControlFlowExpression()
            }
          )
        ),
        expectedCode = """
          Model(
            id = 1,
            name = if (absent) {
              null
            } else {
              "Ada"
            },
          )
        """.trimIndent()
      )
    )

    for ((label, arguments, expectedCode) in cases) {
      val actual = callCode(
        callee = CodeBlock.of("Model"),
        arguments = arguments
      )

      assertWithMessage(label)
        .that(actual.toString())
        .isEqualTo(expectedCode)
    }
  }
}
