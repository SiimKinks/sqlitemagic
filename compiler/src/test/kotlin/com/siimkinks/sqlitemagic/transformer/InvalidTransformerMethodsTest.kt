package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.databaseWithExternalTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class InvalidTransformerMethodsTest {
  private val compiler = SqliteMagicCompiler(::transformerCollectionProcessingSteps)

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTransformerMethods")
  fun `fails for an invalid transformer method`(
    label: String,
    sources: List<SourceFile>,
    expected: List<String>
  ) {
    compiler
      .compile(*sources.toTypedArray())
      .assertCompilationError(*expected.toTypedArray())
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("externalInstanceMemberTransformers")
  fun `fails when configured external transformer methods are instance methods`(
    label: String,
    transformer: SourceFile
  ) {
    val external = SqliteMagicCompilation
      .compile(
        emailValueType(),
        transformer,
        processingStepsFactory = { emptyList() }
      )
      .isOk()
    compiler
      .compile(
        databaseWithExternalTransformer(),
        classpaths = listOf(external.outputDirectory)
      )
      .assertCompilationError(NOT_STATIC)
  }

  companion object {
    private const val NOT_STATIC =
      "Transformer methods must be top-level, declared in an object or companion object, or Java static"
    private const val MISSING_DB_VALUE_TO_OBJECT = "there is missing valid database-value-to-object method"
    private const val MISSING_OBJECT_TO_DB_VALUE = "there is missing valid object-to-database-value method"
    private const val ONE_PARAMETER = "Transformer methods must have one parameter"
    private const val MISMATCHED_TYPES =
      "One transformer method's return type must be the same as other method's first parameter and vice versa"

    @JvmStatic
    fun invalidTransformerMethods() = listOf(
      Arguments.of(
        "transformer method is an instance member",
        listOf(emailValueType(), kotlinInstanceMemberTransformer()),
        listOf(NOT_STATIC)
      ),
      Arguments.of(
        "Java transformer method is an instance member",
        listOf(emailValueType(), javaInstanceMemberTransformer()),
        listOf(NOT_STATIC)
      ),
      Arguments.of(
        "transformer method is an extension function",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "ExtensionTransformer",
            contents = """
              object ExtensionTransformerReceiver

              @ObjectToDbValue
              fun ExtensionTransformerReceiver.emailToString(email: Email): String = email.value

              @DbValueToObject
              fun ExtensionTransformerReceiver.stringToEmail(value: String): Email = Email(value)
              """
          )
        ),
        listOf("Transformer methods must not be extension functions")
      ),
      Arguments.of(
        "transformer method is a suspend function",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "SuspendTransformer",
            contents = """
              @ObjectToDbValue
              suspend fun emailToString(email: Email): String = email.value

              @DbValueToObject
              suspend fun stringToEmail(value: String): Email = Email(value)
              """
          )
        ),
        listOf("Transformer methods must not be suspend functions")
      ),
      Arguments.of(
        "transformer method is private",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "PrivateTransformer",
            contents = """
              @ObjectToDbValue
              private fun emailToString(email: Email): String = email.value

              @DbValueToObject
              private fun stringToEmail(value: String): Email = Email(value)
              """
          )
        ),
        listOf("Transformer methods must not be private")
      ),
      Arguments.of(
        "database-value-to-object method is missing",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "IncompleteTransformer",
            contents = """
              object IncompleteTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email): String = email.value
              }
              """
          )
        ),
        listOf(MISSING_DB_VALUE_TO_OBJECT)
      ),
      Arguments.of(
        "object-to-database-value method is missing",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "IncompleteTransformer",
            contents = """
              object IncompleteTransformer {
                @DbValueToObject
                fun stringToEmail(value: String): Email = Email(value)
              }
              """
          )
        ),
        listOf(MISSING_OBJECT_TO_DB_VALUE)
      ),
      Arguments.of(
        "methods with unrelated type parameters are not paired",
        listOf(
          kotlinTransformerSource(
            name = "GenericMethodTransformer",
            contents = """
              object GenericMethodTransformer {
                @ObjectToDbValue
                fun <T> valueToString(value: T): String = value.toString()

                @DbValueToObject
                fun <T> stringToValue(value: String): T = error(value)
              }
              """
          )
        ),
        listOf(MISSING_DB_VALUE_TO_OBJECT, MISSING_OBJECT_TO_DB_VALUE)
      ),
      Arguments.of(
        "ObjectToDbValue method has no parameters",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "NoParameterTransformer",
            contents = """
              object NoParameterTransformer {
                @ObjectToDbValue
                fun emailToString(): String = "email"

                @DbValueToObject
                fun stringToEmail(value: String): Email = Email(value)
              }
              """
          )
        ),
        listOf(MISSING_OBJECT_TO_DB_VALUE)
      ),
      Arguments.of(
        "DbValueToObject method has no parameters",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "NoParameterTransformer",
            contents = """
              object NoParameterTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email): String = email.value

                @DbValueToObject
                fun stringToEmail(): Email = Email("")
              }
              """
          )
        ),
        listOf(ONE_PARAMETER)
      ),
      Arguments.of(
        "transformer method has multiple parameters",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "MultipleParameterTransformer",
            contents = """
              object MultipleParameterTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email, suffix: String): String = email.value + suffix

                @DbValueToObject
                fun stringToEmail(value: String): Email = Email(value)
              }
              """
          )
        ),
        listOf(ONE_PARAMETER)
      ),
      Arguments.of(
        "transformer method types are different",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "MismatchedTypeTransformer",
            contents = """
              object MismatchedTypeTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email): String = email.value

                @DbValueToObject
                fun intToEmail(value: Int): Email = Email(value.toString())
              }
              """
          )
        ),
        listOf(MISMATCHED_TYPES)
      ),
      Arguments.of(
        "transformer method types have different nullability",
        listOf(
          emailValueType(),
          kotlinTransformerSource(
            name = "MismatchedNullabilityTransformer",
            contents = """
              object MismatchedNullabilityTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email): String = email.value

                @DbValueToObject
                fun stringToEmail(value: String?): Email = Email(value.orEmpty())
              }
              """
          )
        ),
        listOf(MISMATCHED_TYPES)
      ),
      Arguments.of(
        "one method has both transformer annotations",
        listOf(
          kotlinTransformerSource(
            name = "DoubleAnnotatedTransformer",
            contents = """
              @ObjectToDbValue
              @DbValueToObject
              fun <T> transform(value: T): T = value
              """
          )
        ),
        listOf("There must be 2 annotated transform methods")
      )
    )

    @JvmStatic
    fun externalInstanceMemberTransformers() = listOf(
      Arguments.of(
        "Java",
        javaInstanceMemberTransformer(className = "ExternalEmailTransformer")
      ),
      Arguments.of(
        "Kotlin",
        kotlinInstanceMemberTransformer(className = "ExternalEmailTransformer")
      )
    )

    private fun kotlinInstanceMemberTransformer(
      className: String = "InstanceTransformer"
    ) = kotlinTransformerSource(
      name = className,
      contents = """
        class $className {
          @ObjectToDbValue
          fun emailToString(email: Email): String = email.value

          @DbValueToObject
          fun stringToEmail(value: String): Email = Email(value)
        }
        """
    )

    private fun javaInstanceMemberTransformer(
      className: String = "InstanceTransformer"
    ) = javaTransformerSource(
      name = className,
      contents = """
        public final class $className {
          @ObjectToDbValue
          public String emailToString(Email email) {
            return email.getValue();
          }

          @DbValueToObject
          public Email stringToEmail(String value) {
            return new Email(value);
          }
        }
        """
    )
  }
}
