package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class GeneratedCodeApiContractTest {
  @Test
  fun `generated-code API compiles with the opt-in that generated files declare`() {
    compile(
      optIn = "@file:OptIn(InternalSqliteMagicApi::class)",
      program = programs().joinToString(separator = "\n") { it.get()[1].toString() }
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("programs")
  fun `generated-code API needs the opt-in that generated files declare`(
    label: String,
    program: String
  ) {
    compile(
      optIn = "",
      program = program
    ).assertCompilationError(
      "This is SqliteMagic API for generated code and reactive adapters; it may change in any release."
    )
  }

  private fun compile(optIn: String, program: String) = PlainKotlinCompilation.compile(
    fileName = "GeneratedCodeApiConsumer.kt",
    contents = """
      $optIn
      package downstream.generated

      import com.siimkinks.sqlitemagic.Column
      import com.siimkinks.sqlitemagic.GeneratedDatabase
      import com.siimkinks.sqlitemagic.Required
      import com.siimkinks.sqlitemagic.SqlType
      import com.siimkinks.sqlitemagic.Table
      import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
      import com.siimkinks.sqlitemagic.internal.RelationshipRead
      import com.siimkinks.sqlitemagic.internal.parameterEncoding

      class Author

      fun verify(author: Table<Author>, database: GeneratedDatabase) {
        $program
      }
    """
  )

  companion object {
    @JvmStatic
    fun programs() = listOf(
      Arguments.of(
        "a hand-built column",
        "val id = Column<Author, Long, Required, Long, Long, Required, Long>(" +
            """table = author, name = "id", nullness = Required, parameterEncoding = TODO())"""
      ),
      Arguments.of("a table subclass", """class Books : Table<Author>(name = "books", alias = null)"""),
      Arguments.of("a generated-only type", "val read = RelationshipRead.ALWAYS"),
      Arguments.of("a generated-only factory", "val encoding = SqlType.LONG.parameterEncoding()"),
      Arguments.of("a generated database member", "val version = database.dbVersion")
    )
  }
}
