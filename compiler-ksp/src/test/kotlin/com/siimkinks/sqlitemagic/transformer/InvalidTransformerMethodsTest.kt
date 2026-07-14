package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.databaseWithExternalTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.submoduleDatabaseWithExternalTransformer
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import org.junit.jupiter.api.Test

internal class InvalidTransformerMethodsTest : ProcessingStepsTest {
  override val processingSteps
    get() = { env: Environment ->
      listOf(
        DefaultTransformerCollectionStep(env),
        DatabaseConfigurationCollectionStep(env),
        TransformerCollectionStep(env)
      )
    }

  @Test
  fun `fails when transformer method is an instance member`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinInstanceMemberTransformer()
      )
      .assertCompilationError("Transformer methods must be top-level, declared in an object or companion object, or Java static")
  }

  @Test
  fun `fails when Java transformer method is an instance member`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        javaInstanceMemberTransformer()
      )
      .assertCompilationError("Transformer methods must be top-level, declared in an object or companion object, or Java static")
  }

  @Test
  fun `fails when configured external Java transformer methods are instance methods`() {
    externalJavaInstanceTransformerCompilation()
      .compile(databaseWithExternalTransformer())
      .assertCompilationError(
        "Transformer methods must be top-level, declared in an object or companion object, or Java static"
      )
  }

  @Test
  fun `fails when configured external Java transformer methods are instance methods in a submodule database`() {
    externalJavaInstanceTransformerCompilation()
      .compile(submoduleDatabaseWithExternalTransformer())
      .assertCompilationError(
        "Transformer methods must be top-level, declared in an object or companion object, or Java static"
      )
  }

  @Test
  fun `fails when configured external Kotlin transformer methods are instance methods`() {
    externalKotlinInstanceTransformerCompilation()
      .compile(databaseWithExternalTransformer())
      .assertCompilationError(
        "Transformer methods must be top-level, declared in an object or companion object, or Java static"
      )
  }

  @Test
  fun `fails when configured external Kotlin transformer methods are instance methods in a submodule database`() {
    externalKotlinInstanceTransformerCompilation()
      .compile(submoduleDatabaseWithExternalTransformer())
      .assertCompilationError(
        "Transformer methods must be top-level, declared in an object or companion object, or Java static"
      )
  }

  @Test
  fun `fails when transformer method is an extension function`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "ExtensionTransformer",
          contents = """
            object ExtensionTransformerReceiver

            @ObjectToDbValue
            fun ExtensionTransformerReceiver.emailToString(email: Email): String = email.value

            @DbValueToObject
            fun ExtensionTransformerReceiver.stringToEmail(value: String): Email = Email(value)
            """.trimIndent()
        )
      )
      .assertCompilationError("Transformer methods must not be extension functions")
  }

  @Test
  fun `fails when transformer method is a suspend function`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "SuspendTransformer",
          contents = """
            @ObjectToDbValue
            suspend fun emailToString(email: Email): String = email.value

            @DbValueToObject
            suspend fun stringToEmail(value: String): Email = Email(value)
            """.trimIndent()
        )
      )
      .assertCompilationError("Transformer methods must not be suspend functions")
  }

  @Test
  fun `fails when transformer method is private`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "PrivateTransformer",
          contents = """
            @ObjectToDbValue
            private fun emailToString(email: Email): String = email.value

            @DbValueToObject
            private fun stringToEmail(value: String): Email = Email(value)
            """.trimIndent()
        )
      )
      .assertCompilationError("Transformer methods must not be private")
  }

  @Test
  fun `fails when database-value-to-object method is missing`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "IncompleteTransformer",
          contents = """
            object IncompleteTransformer {
              @ObjectToDbValue
              fun emailToString(email: Email): String = email.value
            }
            """.trimIndent()
        )
      )
      .assertCompilationError("there is missing valid database-value-to-object method")
  }

  @Test
  fun `fails when object-to-database-value method is missing`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        kotlinTransformerSource(
          name = "IncompleteTransformer",
          contents = """
            object IncompleteTransformer {
              @DbValueToObject
              fun stringToEmail(value: String): Email = Email(value)
            }
            """.trimIndent()
        )
      )
      .assertCompilationError("there is missing valid object-to-database-value method")
  }

  @Test
  fun `does not pair transformer methods with unrelated type parameters`() {
    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "GenericMethodTransformer",
          contents = """
            object GenericMethodTransformer {
              @ObjectToDbValue
              fun <T> valueToString(value: T): String = value.toString()

              @DbValueToObject
              fun <T> stringToValue(value: String): T = error(value)
            }
            """.trimIndent()
        )
      )
      .assertCompilationError(
        "there is missing valid database-value-to-object method",
        "there is missing valid object-to-database-value method"
      )
  }

  @Test
  fun `fails when transformer ObjectToDbValue method has no parameters`() {
    SqliteMagicCompilation
      .compile(
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
            """.trimIndent()
        )
      )
      .assertCompilationError("there is missing valid object-to-database-value method")
  }

  @Test
  fun `fails when transformer DbValueToObject method has no parameters`() {
    SqliteMagicCompilation
      .compile(
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
            """.trimIndent()
        )
      )
      .assertCompilationError("Transformer methods must have one parameter")
  }

  @Test
  fun `fails when transformer method has multiple parameters`() {
    SqliteMagicCompilation
      .compile(
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
            """.trimIndent()
        )
      )
      .assertCompilationError("Transformer methods must have one parameter")
  }

  @Test
  fun `fails when transformer method types are different`() {
    SqliteMagicCompilation
      .compile(
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
            """.trimIndent()
        )
      )
      .assertCompilationError("One transformer method's return type must be the same as other method's first parameter and vice versa")
  }

  @Test
  fun `fails when transformer method types have different nullability`() {
    SqliteMagicCompilation
      .compile(
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
            """.trimIndent()
        )
      )
      .assertCompilationError("One transformer method's return type must be the same as other method's first parameter and vice versa")
  }

  @Test
  fun `fails when one method has both transformer annotations`() {
    SqliteMagicCompilation
      .compile(
        kotlinTransformerSource(
          name = "DoubleAnnotatedTransformer",
          contents = """
            @ObjectToDbValue
            @DbValueToObject
            fun <T> transform(value: T): T = value
            """.trimIndent()
        )
      )
      .assertCompilationError("There must be 2 annotated transform methods")
  }

  //region helpers
  private fun externalJavaInstanceTransformerCompilation() = SqliteMagicCompilation
    .compile(
      emailValueType(),
      javaInstanceMemberTransformer(className = "ExternalEmailTransformer"),
      processingStepsFactory = { emptyList() }
    )
    .isOk()

  private fun externalKotlinInstanceTransformerCompilation() = SqliteMagicCompilation
    .compile(
      emailValueType(),
      kotlinInstanceMemberTransformer(className = "ExternalEmailTransformer"),
      processingStepsFactory = { emptyList() }
    )
    .isOk()

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
      """.trimIndent()
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
      """.trimIndent()
  )
  //endregion
}
