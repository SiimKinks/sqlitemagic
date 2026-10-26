package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.readDriverSupport
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class NullableTransformerCursorReadContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `required scalar reads explain null decoder results and preserve nullable transformer inputs`() {
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "NullableTransformerCursorRead.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SqliteMagic_NullableDecoded_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_RequiredBoolean_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_RequiredDecoded_Dao
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
            import fixtures.driver.cursor

            data class DecodedValue(val value: String)

            object NullableRuntimeTransformer {
              @ObjectToDbValue
              fun serialize(value: DecodedValue?): String? = value?.value

              @DbValueToObject
              fun decode(value: String?): DecodedValue? = when (value) {
                null -> DecodedValue("sentinel")
                "reject" -> null
                else -> DecodedValue(value)
              }
            }

            @Table
            data class RequiredDecoded(val requiredValue: DecodedValue)

            @Table
            data class NullableDecoded(val nullableValue: DecodedValue?)

            @Table
            data class RequiredBoolean(val requiredBoolean: Boolean)

            object NullableCursorReadDriver {
              @JvmStatic
              fun read(target: String, value: Any?): List<String> = try {
                val decoded = when (target) {
                  "required" -> SqliteMagic_RequiredDecoded_Dao
                    .objectFromCursorPosition(cursor = cursor(value), startIndex = 0)
                  "nullable" -> SqliteMagic_NullableDecoded_Dao
                    .objectFromCursorPosition(cursor = cursor(value), startIndex = 0)
                  "boolean" -> SqliteMagic_RequiredBoolean_Dao
                    .objectFromCursorPosition(cursor = cursor(value), startIndex = 0)
                  else -> error("Unknown target: " + target)
                }
                listOf("value", decoded.toString())
              } catch (failure: Exception) {
                listOf("error", failure.javaClass.simpleName, failure.message.orEmpty())
              }
            }
          """
        ),
        readDriverSupport()
      )
      .isOk()
    val driver = compilation
      .loadClass("$PACKAGE.NullableCursorReadDriver")
      .getMethod("read", String::class.java, Any::class.java)

    data class ReadCase(
      val label: String,
      val target: String,
      val input: Any?,
      val expected: List<String>
    )

    listOf(
      ReadCase(
        label = "required decoded value",
        target = "required",
        input = "accepted",
        expected = listOf("value", "RequiredDecoded(requiredValue=DecodedValue(value=accepted))")
      ),
      ReadCase(
        label = "required null database input becomes sentinel",
        target = "required",
        input = null,
        expected = listOf("value", "RequiredDecoded(requiredValue=DecodedValue(value=sentinel))")
      ),
      ReadCase(
        label = "required null decoded output explains transformer and property",
        target = "required",
        input = "reject",
        expected = listOf(
          "error",
          "IllegalStateException",
          "Transformer $PACKAGE.NullableRuntimeTransformer.decode returned null for required property requiredValue"
        )
      ),
      ReadCase(
        label = "nullable decoded value",
        target = "nullable",
        input = "accepted",
        expected = listOf("value", "NullableDecoded(nullableValue=DecodedValue(value=accepted))")
      ),
      ReadCase(
        label = "nullable null decoded output stays null",
        target = "nullable",
        input = "reject",
        expected = listOf("value", "NullableDecoded(nullableValue=null)")
      ),
      ReadCase(
        label = "nullable null database input stays null",
        target = "nullable",
        input = null,
        expected = listOf("value", "NullableDecoded(nullableValue=null)")
      ),
      ReadCase(
        label = "default Boolean true",
        target = "boolean",
        input = 1L,
        expected = listOf("value", "RequiredBoolean(requiredBoolean=true)")
      ),
      ReadCase(
        label = "default Boolean false",
        target = "boolean",
        input = 0L,
        expected = listOf("value", "RequiredBoolean(requiredBoolean=false)")
      ),
      ReadCase(
        label = "default Boolean null output explains transformer and property",
        target = "boolean",
        input = null,
        expected = listOf(
          "error",
          "IllegalStateException",
          "Transformer com.siimkinks.sqlitemagic.transformer.BooleanTransformer.dbValueToObject " +
              "returned null for required property requiredBoolean"
        )
      )
    ).forEach { case ->
      assertWithMessage(case.label)
        .that(driver.invoke(null, case.target, case.input))
        .isEqualTo(case.expected)
    }
  }
}
