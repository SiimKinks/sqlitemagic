package com.siimkinks.sqlitemagic.transformer

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.nullableObjectTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class InvalidTransformerTargetsTest {
  private val compiler = SqliteMagicCompiler(::transformerCollectionProcessingSteps)

  @ParameterizedTest(name = "Kotlin {0}")
  @ValueSource(
    strings = [
      "Int",
      "String?",
      "ByteArray",
      "Array<Byte>?"
    ]
  )
  fun `fails when deserialized Kotlin type is a supported SQL type`(sqlType: String) {
    compiler
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
            """
        )
      )
      .assertCompilationError("can't have transformers")
  }

  @ParameterizedTest(name = "Java {0}")
  @ValueSource(
    strings = [
      "long",
      "Integer",
      "byte[]",
      "Byte[]"
    ]
  )
  fun `fails when deserialized Java type is a supported SQL type`(sqlType: String) {
    compiler
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
            """
        )
      )
      .assertCompilationError("can't have transformers")
  }

  @Test
  fun `fails when serialized type is not a supported SQL type`() {
    compiler
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
            """
        )
      )
      .assertCompilationError("serialized type must be one of supported SQLite types")
  }

  @Test
  fun `fails when transformed type is also a table`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "Email.kt",
          contents = """
            package $FIXTURE_PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            data class Email(val value: String)
            """
        ),
        objectTransformer()
      )
      .assertCompilationError("Cannot transform object $FIXTURE_PACKAGE.Email which is also annotated with @Table")
  }

  @Test
  fun `reports every transformer function after the first one targeting the same type`() {
    val errorMessage = "Multiple transformers defined for $FIXTURE_PACKAGE.Email"

    compiler
      .compile(
        emailValueType(),
        objectTransformer(className = "FirstEmailTransformer"),
        objectTransformer(className = "SecondEmailTransformer"),
        objectTransformer(className = "ThirdEmailTransformer")
      )
      .assertCompilationError(errorMessage)
      .apply {
        assertThat(
          Regex.escape(errorMessage)
            .toRegex()
            .findAll(messages)
            .count()
        ).isEqualTo(4) // each duplicate transformer reports errors for both of its transformation functions
      }
  }

  @Test
  fun `fails when transformers target nullable and non-nullable variants of the same type`() {
    compiler
      .compile(
        emailValueType(),
        objectTransformer(className = "NonNullEmailTransformer"),
        nullableObjectTransformer()
      )
      .assertCompilationError("Multiple transformers defined for $FIXTURE_PACKAGE.Email")
  }

  @Test
  fun `fails when transformers target aliased and expanded variants of the same type`() {
    compiler
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
            """
        )
      )
      .assertCompilationError("Multiple transformers defined for $FIXTURE_PACKAGE.Email")
  }
}
