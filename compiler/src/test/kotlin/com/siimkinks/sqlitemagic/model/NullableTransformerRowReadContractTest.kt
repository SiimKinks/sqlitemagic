package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.readDriverSupport
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class NullableTransformerRowReadContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `required scalar reads explain null decoder results and nullable reads decode SQL NULL`() {
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "NullableTransformerRowRead.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SqliteMagic_MutableDecoded_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_NullableDecoded_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_OptionalEmbeddedDecoded_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_RequiredBoolean_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_RequiredDecoded_Dao
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
            import fixtures.driver.row

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
            class MutableDecoded {
              var nullableValue: DecodedValue? = DecodedValue("initial")

              override fun toString() = "MutableDecoded(nullableValue=" + nullableValue + ")"
            }

            @Table
            data class RequiredBoolean(val requiredBoolean: Boolean)

            data class EmbeddedDecoded(
              val requiredValue: DecodedValue,
              val note: String
            )

            @Table
            data class OptionalEmbeddedDecoded(@Embedded val details: EmbeddedDecoded?)

            object NullableRowReadDriver {
              @JvmStatic
              fun read(target: String, values: List<Any?>): List<String> = try {
                val value = values.firstOrNull()
                val decoded = when (target) {
                  "required" -> SqliteMagic_RequiredDecoded_Dao
                    .objectFromRow(statement = row(value), startIndex = 0)
                  "nullable" -> SqliteMagic_NullableDecoded_Dao
                    .objectFromRow(statement = row(value), startIndex = 0)
                  "nullable selected" -> SqliteMagic_NullableDecoded_Dao
                    .objectFromSelectedPositions(statement = row(value), positions = intArrayOf(0))
                  "nullable unselected" -> SqliteMagic_NullableDecoded_Dao
                    .objectFromSelectedPositions(statement = row(value), positions = intArrayOf(-1))
                  "mutable" -> SqliteMagic_MutableDecoded_Dao
                    .objectFromRow(statement = row(value), startIndex = 0)
                  "mutable unselected" -> SqliteMagic_MutableDecoded_Dao
                    .objectFromSelectedPositions(statement = row(value), positions = intArrayOf(-1))
                  "boolean" -> SqliteMagic_RequiredBoolean_Dao
                    .objectFromRow(statement = row(value), startIndex = 0)
                  "embedded" -> SqliteMagic_OptionalEmbeddedDecoded_Dao
                    .objectFromRow(statement = row(*values.toTypedArray()), startIndex = 0)
                  "embedded selected" -> SqliteMagic_OptionalEmbeddedDecoded_Dao
                    .objectFromSelectedPositions(statement = row(*values.toTypedArray()), positions = intArrayOf(0, 1))
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
      .loadClass("$PACKAGE.NullableRowReadDriver")
      .getMethod("read", String::class.java, List::class.java)

    data class ReadCase(
      val label: String,
      val target: String,
      val input: List<Any?>,
      val expected: List<String>
    )

    listOf(
      ReadCase(
        label = "required decoded value",
        target = "required",
        input = listOf("accepted"),
        expected = listOf("value", "RequiredDecoded(requiredValue=DecodedValue(value=accepted))")
      ),
      ReadCase(
        label = "required null decoded output explains transformer and property",
        target = "required",
        input = listOf("reject"),
        expected = listOf(
          "error",
          "IllegalStateException",
          "Transformer $PACKAGE.NullableRuntimeTransformer.decode returned null for required property requiredValue"
        )
      ),
      ReadCase(
        label = "nullable decoded value",
        target = "nullable",
        input = listOf("accepted"),
        expected = listOf("value", "NullableDecoded(nullableValue=DecodedValue(value=accepted))")
      ),
      ReadCase(
        label = "nullable null decoded output stays null",
        target = "nullable",
        input = listOf("reject"),
        expected = listOf("value", "NullableDecoded(nullableValue=null)")
      ),
      ReadCase(
        label = "nullable null database input decodes through the transformer",
        target = "nullable",
        input = listOf(null),
        expected = listOf("value", "NullableDecoded(nullableValue=DecodedValue(value=sentinel))")
      ),
      ReadCase(
        label = "partial read decodes a selected null database input",
        target = "nullable selected",
        input = listOf(null),
        expected = listOf("value", "NullableDecoded(nullableValue=DecodedValue(value=sentinel))")
      ),
      ReadCase(
        label = "partial read leaves an unselected nullable value null",
        target = "nullable unselected",
        input = listOf(null),
        expected = listOf("value", "NullableDecoded(nullableValue=null)")
      ),
      ReadCase(
        label = "mutable null database input decodes through the transformer",
        target = "mutable",
        input = listOf(null),
        expected = listOf("value", "MutableDecoded(nullableValue=DecodedValue(value=sentinel))")
      ),
      ReadCase(
        label = "mutable null decoded output assigns null",
        target = "mutable",
        input = listOf("reject"),
        expected = listOf("value", "MutableDecoded(nullableValue=null)")
      ),
      ReadCase(
        label = "mutable unselected value keeps its initializer",
        target = "mutable unselected",
        input = listOf(null),
        expected = listOf("value", "MutableDecoded(nullableValue=DecodedValue(value=initial))")
      ),
      ReadCase(
        label = "default Boolean true",
        target = "boolean",
        input = listOf(1L),
        expected = listOf("value", "RequiredBoolean(requiredBoolean=true)")
      ),
      ReadCase(
        label = "default Boolean false",
        target = "boolean",
        input = listOf(0L),
        expected = listOf("value", "RequiredBoolean(requiredBoolean=false)")
      ),
      ReadCase(
        label = "present nullable embedded decodes a null database input of a required value",
        target = "embedded",
        input = listOf(null, "x"),
        expected = listOf(
          "value",
          "OptionalEmbeddedDecoded(details=EmbeddedDecoded(requiredValue=DecodedValue(value=sentinel), note=x))"
        )
      ),
      ReadCase(
        label = "partial read of a present nullable embedded decodes a null database input of a required value",
        target = "embedded selected",
        input = listOf(null, "x"),
        expected = listOf(
          "value",
          "OptionalEmbeddedDecoded(details=EmbeddedDecoded(requiredValue=DecodedValue(value=sentinel), note=x))"
        )
      ),
      ReadCase(
        label = "nullable embedded whose columns are all NULL reads null",
        target = "embedded",
        input = listOf(null, null),
        expected = listOf("value", "OptionalEmbeddedDecoded(details=null)")
      )
    )
      .forEach { case ->
        assertWithMessage(case.label)
          .that(driver.invoke(null, case.target, case.input))
          .isEqualTo(case.expected)
      }
  }
}
