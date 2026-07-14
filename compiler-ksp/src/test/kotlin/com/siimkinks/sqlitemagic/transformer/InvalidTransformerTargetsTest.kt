package com.siimkinks.sqlitemagic.transformer

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.nullableObjectTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class InvalidTransformerTargetsTest : ProcessingStepsTest {
  override val processingSteps
    get() = { env: Environment ->
      listOf(
        DefaultTransformerCollectionStep(env),
        DatabaseConfigurationCollectionStep(env),
        TransformerCollectionStep(env)
      )
    }

  @ParameterizedTest(name = "Kotlin {0}")
  @ValueSource(
    strings = [
      "Byte", "Byte?",
      "Short", "Short?",
      "Int", "Int?",
      "Long", "Long?",
      "Float", "Float?",
      "Double", "Double?",
      "ByteArray", "ByteArray?",
      "Array<Byte>", "Array<Byte>?",
      "String", "String?"
    ]
  )
  fun `fails when deserialized Kotlin type is a supported SQL type`(sqlType: String) {
    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "SqlTypeTransformer",
          contents = """
            object SqlTypeTransformer {
              @ObjectToDbValue
              fun sqlTypeToString(value: $sqlType): String = value.toString()

              @DbValueToObject
              fun stringToSqlType(value: String): $sqlType = error(value)
            }
            """.trimIndent()
        )
      )
      .assertCompilationError("can't have transformers")
  }

  @ParameterizedTest(name = "Java {0}")
  @ValueSource(
    strings = [
      "byte", "Byte",
      "short", "Short",
      "int", "Integer",
      "long", "Long",
      "float", "Float",
      "double", "Double",
      "byte[]", "Byte[]",
      "String"
    ]
  )
  fun `fails when deserialized Java type is a supported SQL type`(sqlType: String) {
    SqliteMagicCompilation
      .compile(
        javaTransformerSource(
          name = "SqlTypeTransformer",
          contents = """
            public final class SqlTypeTransformer {
              @ObjectToDbValue
              public static String sqlTypeToString($sqlType value) {
                return String.valueOf(value);
              }

              @DbValueToObject
              public static $sqlType stringToSqlType(String value) {
                throw new IllegalArgumentException(value);
              }
            }
            """.trimIndent()
        )
      )
      .assertCompilationError("can't have transformers")
  }

  @Test
  fun `fails when serialized type is not a supported SQL type`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "UnsupportedSerializedTypeTransformer",
          contents = """
            data class SerializedEmail(val value: String)

            object UnsupportedSerializedTypeTransformer {
              @ObjectToDbValue
              fun emailToSerializedEmail(email: Email): SerializedEmail = SerializedEmail(email.value)

              @DbValueToObject
              fun serializedEmailToEmail(value: SerializedEmail): Email = Email(value.value)
            }
            """.trimIndent()
        )
      )
      .assertCompilationError("serialized type must be one of supported SQLite types")
  }

  @Test
  fun `fails when transformed type is also a table`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Email.kt",
          contents = """
            package $FIXTURE_PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Email(val value: String)
            """.trimIndent()
        ),
        objectTransformer()
      )
      .assertCompilationError("Cannot transform object $FIXTURE_PACKAGE.Email which is also annotated with @Table")
  }

  @Test
  fun `fails when multiple transformers target the same type`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(className = "FirstEmailTransformer"),
        objectTransformer(className = "SecondEmailTransformer")
      )
      .assertCompilationError("Multiple transformers defined for $FIXTURE_PACKAGE.Email")
  }

  @Test
  fun `reports every transformer function after the first one targeting the same type`() {
    val errorMessage = "Multiple transformers defined for $FIXTURE_PACKAGE.Email"

    SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(className = "FirstEmailTransformer"),
        objectTransformer(className = "SecondEmailTransformer"),
        objectTransformer(className = "ThirdEmailTransformer")
      )
      .assertCompilationError(errorMessage)
      .apply {
        assertThat(
          Regex.escape("Multiple transformers defined for $FIXTURE_PACKAGE.Email")
            .toRegex()
            .findAll(result.messages)
            .count()
        ).isEqualTo(4) // each duplicate transformer reports errors for both of its transformation functions
      }
  }

  @Test
  fun `fails when transformers target nullable and non-nullable variants of the same type`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(className = "NonNullEmailTransformer"),
        nullableObjectTransformer()
      )
      .assertCompilationError("Multiple transformers defined for $FIXTURE_PACKAGE.Email")
  }

  @Test
  fun `fails when transformers target aliased and expanded variants of the same type`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(className = "ExpandedEmailTransformer"),
        kotlinTransformerSource(
          name = "AliasedEmailTransformer",
          contents = """
            typealias EmailAlias = Email

            object AliasedEmailTransformer {
              @ObjectToDbValue
              fun emailToString(email: EmailAlias): String = email.value

              @DbValueToObject
              fun stringToEmail(value: String): EmailAlias = Email(value)
            }
            """.trimIndent()
        )
      )
      .assertCompilationError("Multiple transformers defined for $FIXTURE_PACKAGE.Email")
  }
}
