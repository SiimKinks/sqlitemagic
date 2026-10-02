package com.siimkinks.sqlitemagic.transformer

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.nullableObjectTransformer
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class TransformerCodeGenerationStepTest : ProcessingStepsTest {
  override val processingSteps = ::transformerCodeGenerationProcessingSteps

  @Nested
  inner class ColumnTypesAndNullability {
    @Test
    fun `generates transformer column`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          objectTransformer()
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "public class EmailColumn<T, N>(",
            ") : Column<Email, Email, Email, T, N>(",
            "valueParser: Utils.ValueParser<String>",
            "valueAdapter = ColumnValueAdapter.transformed(",
            "toDb = EmailTransformer::emailToString",
            "fromDb = EmailTransformer::stringToEmail",
            "private val table: Table<T>",
            "private val name: String",
            "private val valueParser: Utils.ValueParser<String>",
            "private val nullable: Boolean",
            "override fun `as`(alias: String): EmailColumn<T, N> = EmailColumn<T, N>(",
            "table = table,",
            "name = name,",
            "valueParser = valueParser,",
            "nullable = nullable,",
            "alias = alias"
          )
          generatedSource.assertDoesNotContain(
            "source: EmailColumn<T, N>",
            "super(source = source, alias = alias)",
            "private val columnTable",
            "private val columnValueParser",
            "private fun serializeColumnValue",
            "private fun deserializeColumnValue",
            "override fun toSqlArg",
            "override fun <V> getFromCursor"
          )
        }
    }

    @Test
    fun `guards nullable serializer results and forwards null values to deserializer`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          nullableObjectTransformer()
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          // Column type
          generatedSource.assertContains(
            ": Column<Email, Email, Email, T, N>("
          )

          // Serialization
          generatedSource.assertContains(
            "valueAdapter = ColumnValueAdapter.transformedNullableInput(",
            "toDb = NullableEmailTransformer::emailToString"
          )

          // Deserialization
          generatedSource.assertContains(
            "fromDb = NullableEmailTransformer::stringToEmail"
          )
          generatedSource.assertDoesNotContain(
            "super.getFromCursor<String>(cursor) ?: return null",
            "super.getFromStatement<String>(statement) ?: return null"
          )
        }
    }

    @Test
    fun `forwards null database values to nullable Kotlin deserializer`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "NullableSerializedValueTransformer",
            contents = """
              object NullableSerializedValueTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email): String? = email.value

                @DbValueToObject
                fun stringToEmail(value: String?): Email = Email(value.orEmpty())
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "ColumnValueAdapter.transformedNullableInput(",
            "fromDb = NullableSerializedValueTransformer::stringToEmail"
          )
          generatedSource.assertDoesNotContain(
            "super.getFromCursor<String>(cursor) ?: return null",
            "super.getFromStatement<String>(statement) ?: return null"
          )
        }
    }

    @Test
    fun `removes only root nullability from parameterized column types`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "NullableEmailListTransformer",
            contents = """
              object NullableEmailListTransformer {
                @ObjectToDbValue
                fun emailsToString(emails: List<Email?>?): String? = emails?.size?.toString()

                @DbValueToObject
                fun stringToEmails(value: String?): List<Email?>? = when (value) {
                  null -> null
                  else -> emptyList()
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("List_EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            ": Column<List<Email?>, List<Email?>, List<Email?>, T, N>"
          )
          generatedSource.assertDoesNotContain(
            ": Column<List<Email?>?, List<Email?>?, List<Email?>?, T, N>"
          )
        }
    }

    @Test
    fun `generates numeric column for numeric serialized type`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "NumericEmailTransformer",
            contents = """
              object NumericEmailTransformer {
                @ObjectToDbValue
                fun emailToLong(email: Email): Long = email.value.length.toLong()

                @DbValueToObject
                fun longToEmail(value: Long): Email = Email(value.toString())
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            ": NumericColumn<Email, Email, Email, T, N>(",
            "ColumnValueAdapter.transformed(",
            "fromDb = NumericEmailTransformer::longToEmail"
          )
        }
    }

    @Test
    fun `generates numeric column for nullable numeric serialized type`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "NullableNumericEmailTransformer",
            contents = """
              object NullableNumericEmailTransformer {
                @ObjectToDbValue
                fun emailToLong(email: Email): Long? = email.value.length.toLong()

                @DbValueToObject
                fun longToEmail(value: Long?): Email = Email((value ?: 0L).toString())
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          // Column type
          generatedSource.assertContains(
            ": NumericColumn<Email, Email, Email, T, N>("
          )

          // Serialization
          generatedSource.assertContains(
            "toDb = NullableNumericEmailTransformer::emailToLong"
          )

          // Deserialization
          generatedSource.assertContains(
            "ColumnValueAdapter.transformedNullableInput(",
            "fromDb = NullableNumericEmailTransformer::longToEmail"
          )
          generatedSource.assertDoesNotContain(
            "super.getFromCursor<Long>(cursor) ?: return null"
          )
        }
    }

    @Test
    fun `generates column for byte array serialized type`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "ByteArrayEmailTransformer",
            contents = """
              object ByteArrayEmailTransformer {
                @ObjectToDbValue
                fun emailToBytes(email: Email): ByteArray = email.value.encodeToByteArray()

                @DbValueToObject
                fun bytesToEmail(value: ByteArray): Email = Email(value.decodeToString())
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            ": Column<Email, Email, Email, T, N>(",
            "toDb = ByteArrayEmailTransformer::emailToBytes",
            "fromDb = ByteArrayEmailTransformer::bytesToEmail"
          )
        }
    }
  }

  @Nested
  inner class KotlinTransformerReferences {
    @Test
    fun `references top-level transformer functions directly`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "EmailTransformer",
            contents = """
              @ObjectToDbValue
              fun emailToString(email: Email): String = email.value

              @DbValueToObject
              fun stringToEmail(value: String): Email = Email(value)
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "toDb = ::emailToString",
            "fromDb = ::stringToEmail"
          )
        }
    }

    @Test
    fun `references companion object transformer functions directly`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "EmailTransformer",
            contents = """
              class EmailTransformer {
                companion object {
                  @ObjectToDbValue
                  fun emailToString(email: Email): String = email.value

                  @DbValueToObject
                  fun stringToEmail(value: String): Email = Email(value)
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "toDb = EmailTransformer.Companion::emailToString",
            "ColumnValueAdapter.transformed(",
            "fromDb = EmailTransformer.Companion::stringToEmail"
          )
        }
    }

    @Test
    fun `references transformer functions in nested object directly`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "NestedEmailTransformer",
            contents = """
              object TransformerContainer {
                object NestedEmailTransformer {
                  @ObjectToDbValue
                  fun emailToString(email: Email): String = email.value

                  @DbValueToObject
                  fun stringToEmail(value: String): Email = Email(value)
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "toDb = TransformerContainer.NestedEmailTransformer::emailToString",
            "fromDb = TransformerContainer.NestedEmailTransformer::stringToEmail"
          )
        }
    }

    @Test
    fun `escapes keyword transformer function names`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "KeywordEmailTransformer",
            contents = """
              object KeywordEmailTransformer {
                @ObjectToDbValue
                fun `when`(email: Email): String = email.value

                @DbValueToObject
                fun `when`(value: String): Email = Email(value)
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "toDb = KeywordEmailTransformer::`when`",
            "fromDb = KeywordEmailTransformer::`when`"
          )
        }
    }

    @Test
    fun `resolves colliding top-level transformer function names from different packages`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          SourceFile.kotlin(
            name = "EmailSerializer.kt",
            contents = """
              package $FIXTURE_PACKAGE.serializer

              import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
              import $FIXTURE_PACKAGE.Email

              @ObjectToDbValue
              fun transform(email: Email): String = email.value
              """
          ),
          SourceFile.kotlin(
            name = "EmailDeserializer.kt",
            contents = """
              package $FIXTURE_PACKAGE.deserializer

              import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
              import $FIXTURE_PACKAGE.Email

              @DbValueToObject
              fun transform(value: String): Email = Email(value)
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "$FIXTURE_PACKAGE.serializer.transform",
            "$FIXTURE_PACKAGE.deserializer.transform"
          )
        }
    }
  }

  @Nested
  inner class JavaTransformers {
    @Test
    fun `does not invoke Java non-null deserializer for null database values`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          javaTransformerSource(
            name = "NonNullJavaEmailTransformer",
            contents = """
              import androidx.annotation.NonNull;

              public final class NonNullJavaEmailTransformer {
                @NonNull
                @ObjectToDbValue
                public static String emailToString(@NonNull Email email) {
                  return email.getValue();
                }

                @NonNull
                @DbValueToObject
                public static Email stringToEmail(@NonNull String value) {
                  return new Email(value);
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "ColumnValueAdapter.transformed(",
            "fromDb = NonNullJavaEmailTransformer::stringToEmail"
          )
        }
    }

    @Test
    fun `forwards null database values to Java nullable deserializer`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          javaTransformerSource(
            name = "NullableJavaEmailTransformer",
            contents = """
              import androidx.annotation.NonNull;
              import androidx.annotation.Nullable;

              public final class NullableJavaEmailTransformer {
                @Nullable
                @ObjectToDbValue
                public static String emailToString(@NonNull Email email) {
                  return email.getValue();
                }

                @NonNull
                @DbValueToObject
                public static Email stringToEmail(@Nullable String value) {
                  return new Email(value == null ? "" : value);
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            "ColumnValueAdapter.transformedNullableInput(",
            "fromDb = NullableJavaEmailTransformer::stringToEmail"
          )
          generatedSource.assertDoesNotContain(
            "super.getFromCursor<String>(cursor) ?: return null",
            "super.getFromStatement<String>(statement) ?: return null"
          )
        }
    }

    @Test
    fun `generates transformer calls for Java primitive serialized type`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          javaTransformerSource(
            name = "PrimitiveJavaEmailTransformer",
            contents = """
              public final class PrimitiveJavaEmailTransformer {
                @ObjectToDbValue
                public static long emailToLong(Email email) {
                  return email.getValue().length();
                }

                @DbValueToObject
                public static Email longToEmail(long value) {
                  return new Email(Long.toString(value));
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            ": NumericColumn<Email, Email, Email, T, N>(",
            "toDb = PrimitiveJavaEmailTransformer::emailToLong",
            "fromDb = PrimitiveJavaEmailTransformer::longToEmail"
          )
        }
    }

    @Test
    fun `guards platform-null Java serializer results`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          javaTransformerSource(
            name = "JavaEmailTransformer",
            contents = """
              public final class JavaEmailTransformer {
                @ObjectToDbValue
                public static String emailToString(Email email) {
                  return email.getValue();
                }

                @DbValueToObject
                public static Email stringToEmail(String value) {
                  return new Email(value);
                }
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("EmailColumn.kt") { generatedSource ->
          // Serialization
          generatedSource.assertContains(
            "toDb = JavaEmailTransformer::emailToString"
          )

          // Deserialization
          generatedSource.assertContains(
            "fromDb = JavaEmailTransformer::stringToEmail"
          )
        }
    }
  }

  @Nested
  inner class ParameterizedTypes {
    @Test
    fun `generates columns for parameterized transformed types`() {
      SqliteMagicCompilation
        .compile(
          emailValueType(),
          kotlinTransformerSource(
            name = "EmailListTransformer",
            contents = """
              object EmailListTransformer {
                @ObjectToDbValue
                fun emailsToString(emails: List<Email>): String = emails.joinToString(transform = Email::value)

                @DbValueToObject
                fun stringToEmails(value: String): List<Email> = value.split(',').map(::Email)
              }
              """
          )
        )
        .isOk()
        .withGeneratedSource("List_EmailColumn.kt") { generatedSource ->
          generatedSource.assertContains(
            ": Column<List<Email>, List<Email>, List<Email>, T, N>"
          )
        }
    }
  }

  @Nested
  inner class ProcessingLifecycle {
    @Test
    fun `does not generate columns for default transformers`() {
      assertThat(
        SqliteMagicCompilation
          .compile(emailValueType())
          .isOk()
          .generatedSourceNames()
      ).isEmpty()
    }

    @Test
    fun `generates columns for transformers discovered in a later round`() {
      val result = SqliteMagicCompilation
        .compile(
          emailValueType(),
          processingStepsFactory = { env ->
            listOf(
              DefaultTransformerCollectionStep(env),
              TransformerCollectionStep(env),
              TransformerCodeGenerationStep(env),
              GeneratingTransformerStep(env)
            )
          }
        )
        .isOk()

      assertThat(result.generatedSourceNames()).contains("EmailColumn.kt")
    }
  }

  private class GeneratingTransformerStep(
    private val environment: Environment
  ) : ProcessingStep {
    private var isGenerated = false

    override fun process(resolver: Resolver): ProcessingStepResult = when {
      isGenerated -> Continue
      else -> {
        val emailSource = resolver
          .getAllFiles()
          .first { it.fileName == "Email.kt" }
        environment.codeGenerator
          .createNewFile(
            dependencies = Dependencies(false, emailSource),
            packageName = FIXTURE_PACKAGE,
            fileName = "GeneratedEmailTransformer"
          )
          .bufferedWriter()
          .use { output ->
            output.write(
              """
              package $FIXTURE_PACKAGE

              import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
              import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

              object GeneratedEmailTransformer {
                @ObjectToDbValue
                fun emailToString(email: Email): String = email.value

                @DbValueToObject
                fun stringToEmail(value: String): Email = Email(value)
              }
              """
            )
          }
        isGenerated = true
        Continue
      }
    }
  }
}
