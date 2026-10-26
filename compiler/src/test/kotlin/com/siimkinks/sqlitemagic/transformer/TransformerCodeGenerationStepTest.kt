package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.processing.Dependencies
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.javaTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class TransformerCodeGenerationStepTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("descriptorCases")
  fun `generates the transformer descriptor`(descriptorCase: DescriptorCase) {
    GENERATED
      .isOk()
      .withGeneratedSource(descriptorCase.descriptorFile) {
        it.assertContainsUnwrapped(*descriptorCase.expected.toTypedArray())
      }
  }

  @Test
  fun `generates descriptors for transformers discovered in a later round`() {
    SqliteMagicCompilation
      .compile(
        emailValueType(),
        processingStepsFactory = { environment ->
          listOf(
            DefaultTransformerCollectionStep(environment),
            TransformerCollectionStep(environment),
            TransformerCodeGenerationStep(environment),
            GeneratedSourceStep(
              environment = environment,
              fileName = "GeneratedEmailTransformer",
              contents = """
                package $FIXTURE_PACKAGE

                import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
                import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

                object GeneratedEmailTransformer {
                  @ObjectToDbValue
                  fun emailToString(email: Email): String = email.value

                  @DbValueToObject
                  fun stringToEmail(value: String): Email = Email(value)
                }
              """,
              packageName = FIXTURE_PACKAGE,
              dependencies = { resolver ->
                Dependencies(
                  aggregating = false,
                  resolver
                    .getAllFiles()
                    .first { it.fileName == "Email.kt" }
                )
              }
            )
          )
        }
      )
      .isOk()
      .assertGeneratedSources("EmailSqlType.kt")
  }

  class DescriptorCase(
    private val label: String,
    val sources: List<SourceFile>,
    val descriptorFile: String,
    val expected: List<String>
  ) {
    override fun toString() = label
  }

  companion object {
    private val DESCRIPTOR_CASES = listOf(
      DescriptorCase(
        label = "guards nullable serializer results and forwards null values to deserializer",
        sources = listOf(
          kotlinTransformerSource(
            name = "NullableEmailTransformer",
            contents = """
              data class NullableEmail(val value: String)

              object NullableEmailTransformer {
                @ObjectToDbValue
                fun emailToString(email: NullableEmail?): String? = email?.value

                @DbValueToObject
                fun stringToEmail(value: String?): NullableEmail? = value?.let(::NullableEmail)
              }
              """
          )
        ),
        descriptorFile = "NullableEmailSqlType.kt",
        expected = listOf(
          "EncodingType<NullableEmail?, NullableEmail?, SqlNullable, SqlNullable> = nullableReadType(",
          "storage = SqlStorage.STRING",
          "encode = NullableEmailTransformer::emailToString",
          "decode = NullableEmailTransformer::stringToEmail",
          "encodingNullness = SqlNullable"
        )
      ),
      DescriptorCase(
        label = "removes only root nullability from parameterized descriptor types",
        sources = listOf(
          kotlinTransformerSource(
            name = "NullableEmailListTransformer",
            contents = """
              data class ListedEmail(val value: String)

              object NullableEmailListTransformer {
                @ObjectToDbValue
                fun emailsToString(emails: List<ListedEmail?>?): String? = emails?.size?.toString()

                @DbValueToObject
                fun stringToEmails(value: String?): List<ListedEmail?>? = when (value) {
                  null -> null
                  else -> emptyList()
                }
              }
              """
          )
        ),
        descriptorFile = "List_ListedEmailSqlType.kt",
        expected = listOf(
          "EncodingType<List<ListedEmail?>?, List<ListedEmail?>?, SqlNullable, SqlNullable> = nullableReadType(",
          "storage = SqlStorage.STRING",
          "encode = NullableEmailListTransformer::emailsToString",
          "decode = NullableEmailListTransformer::stringToEmails",
          "encodingNullness = SqlNullable"
        )
      ),
      DescriptorCase(
        label = "generates numeric descriptor for numeric serialized type",
        sources = listOf(
          kotlinTransformerSource(
            name = "NumericEmailTransformer",
            contents = """
              data class NumericEmail(val value: String)

              object NumericEmailTransformer {
                @ObjectToDbValue
                fun emailToLong(email: NumericEmail): Long = email.value.length.toLong()

                @DbValueToObject
                fun longToEmail(value: Long): NumericEmail = NumericEmail(value.toString())
              }
              """
          )
        ),
        descriptorFile = "NumericEmailSqlType.kt",
        expected = listOf(
          "EncodingType<NumericEmail, NumericEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.LONG",
          "encode = NumericEmailTransformer::emailToLong",
          "decode = NumericEmailTransformer::longToEmail",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "generates descriptor for byte array serialized type",
        sources = listOf(
          kotlinTransformerSource(
            name = "ByteArrayEmailTransformer",
            contents = """
              data class ByteArrayEmail(val value: String)

              object ByteArrayEmailTransformer {
                @ObjectToDbValue
                fun emailToBytes(email: ByteArrayEmail): ByteArray = email.value.encodeToByteArray()

                @DbValueToObject
                fun bytesToEmail(value: ByteArray): ByteArrayEmail = ByteArrayEmail(value.decodeToString())
              }
              """
          )
        ),
        descriptorFile = "ByteArrayEmailSqlType.kt",
        expected = listOf(
          "EncodingType<ByteArrayEmail, ByteArrayEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.BYTE_ARRAY",
          "encode = ByteArrayEmailTransformer::emailToBytes",
          "decode = ByteArrayEmailTransformer::bytesToEmail",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "references companion object transformer functions directly",
        sources = listOf(
          kotlinTransformerSource(
            name = "EmailTransformer",
            contents = """
              data class CompanionEmail(val value: String)

              class EmailTransformer {
                companion object {
                  @ObjectToDbValue
                  fun emailToString(email: CompanionEmail): String = email.value

                  @DbValueToObject
                  fun stringToEmail(value: String): CompanionEmail = CompanionEmail(value)
                }
              }
              """
          )
        ),
        descriptorFile = "CompanionEmailSqlType.kt",
        expected = listOf(
          "EncodingType<CompanionEmail, CompanionEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = EmailTransformer.Companion::emailToString",
          "decode = EmailTransformer.Companion::stringToEmail",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "references transformer functions in nested object directly",
        sources = listOf(
          kotlinTransformerSource(
            name = "NestedEmailTransformer",
            contents = """
              data class NestedEmail(val value: String)

              object TransformerContainer {
                object NestedEmailTransformer {
                  @ObjectToDbValue
                  fun emailToString(email: NestedEmail): String = email.value

                  @DbValueToObject
                  fun stringToEmail(value: String): NestedEmail = NestedEmail(value)
                }
              }
              """
          )
        ),
        descriptorFile = "NestedEmailSqlType.kt",
        expected = listOf(
          "EncodingType<NestedEmail, NestedEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = TransformerContainer.NestedEmailTransformer::emailToString",
          "decode = TransformerContainer.NestedEmailTransformer::stringToEmail",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "escapes keyword transformer function names",
        sources = listOf(
          kotlinTransformerSource(
            name = "KeywordEmailTransformer",
            contents = """
              data class KeywordEmail(val value: String)

              object KeywordEmailTransformer {
                @ObjectToDbValue
                fun `when`(email: KeywordEmail): String = email.value

                @DbValueToObject
                fun `when`(value: String): KeywordEmail = KeywordEmail(value)
              }
              """
          )
        ),
        descriptorFile = "KeywordEmailSqlType.kt",
        expected = listOf(
          "EncodingType<KeywordEmail, KeywordEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = KeywordEmailTransformer::`when`",
          "decode = KeywordEmailTransformer::`when`",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "resolves colliding top-level transformer function names from different packages",
        sources = listOf(
          emailValueType(className = "PackagedEmail"),
          SourceFile.kotlin(
            name = "EmailSerializer.kt",
            contents = """
              package $FIXTURE_PACKAGE.serializer

              import $FIXTURE_PACKAGE.PackagedEmail
              import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

              @ObjectToDbValue
              fun transform(email: PackagedEmail): String = email.value
              """
          ),
          SourceFile.kotlin(
            name = "EmailDeserializer.kt",
            contents = """
              package $FIXTURE_PACKAGE.deserializer

              import $FIXTURE_PACKAGE.PackagedEmail
              import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject

              @DbValueToObject
              fun transform(value: String): PackagedEmail = PackagedEmail(value)
              """
          )
        ),
        descriptorFile = "PackagedEmailSqlType.kt",
        expected = listOf(
          "EncodingType<PackagedEmail, PackagedEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = ::serializerTransform",
          "decode = ::deserializerTransform",
          "encodingNullness = Required",
          "import com.example.sqlitemagic.transformers.deserializer.transform as deserializerTransform",
          "import com.example.sqlitemagic.transformers.serializer.transform as serializerTransform"
        )
      ),
      DescriptorCase(
        label = "does not invoke Java non-null deserializer for null database values",
        sources = listOf(
          emailValueType(className = "NonNullJavaEmail"),
          javaTransformerSource(
            name = "NonNullJavaEmailTransformer",
            contents = """
              import androidx.annotation.NonNull;

              public final class NonNullJavaEmailTransformer {
                @NonNull
                @ObjectToDbValue
                public static String emailToString(@NonNull NonNullJavaEmail email) {
                  return email.getValue();
                }

                @NonNull
                @DbValueToObject
                public static NonNullJavaEmail stringToEmail(@NonNull String value) {
                  return new NonNullJavaEmail(value);
                }
              }
              """
          )
        ),
        descriptorFile = "NonNullJavaEmailSqlType.kt",
        expected = listOf(
          "EncodingType<NonNullJavaEmail, NonNullJavaEmail, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = NonNullJavaEmailTransformer::emailToString",
          "decode = NonNullJavaEmailTransformer::stringToEmail",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "forwards null database values to Java nullable deserializer",
        sources = listOf(
          emailValueType(className = "NullableJavaEmail"),
          javaTransformerSource(
            name = "NullableJavaEmailTransformer",
            contents = """
              import androidx.annotation.NonNull;
              import androidx.annotation.Nullable;

              public final class NullableJavaEmailTransformer {
                @Nullable
                @ObjectToDbValue
                public static String emailToString(@NonNull NullableJavaEmail email) {
                  return email.getValue();
                }

                @NonNull
                @DbValueToObject
                public static NullableJavaEmail stringToEmail(@Nullable String value) {
                  return new NullableJavaEmail(value == null ? "" : value);
                }
              }
              """
          )
        ),
        descriptorFile = "NullableJavaEmailSqlType.kt",
        expected = listOf(
          "EncodingType<NullableJavaEmail, NullableJavaEmail, SqlNullable, SqlNullable> = nullableReadType(",
          "storage = SqlStorage.STRING",
          "encode = NullableJavaEmailTransformer::emailToString",
          "decode = NullableJavaEmailTransformer::stringToEmail",
          "encodingNullness = SqlNullable"
        )
      ),
      DescriptorCase(
        label = "generates transformer calls for Java primitive serialized type",
        sources = listOf(
          emailValueType(className = "PrimitiveJavaEmail"),
          javaTransformerSource(
            name = "PrimitiveJavaEmailTransformer",
            contents = """
              public final class PrimitiveJavaEmailTransformer {
                @ObjectToDbValue
                public static long emailToLong(PrimitiveJavaEmail email) {
                  return email.getValue().length();
                }

                @DbValueToObject
                public static PrimitiveJavaEmail longToEmail(long value) {
                  return new PrimitiveJavaEmail(Long.toString(value));
                }
              }
              """
          )
        ),
        descriptorFile = "PrimitiveJavaEmailSqlType.kt",
        expected = listOf(
          "EncodingType<PrimitiveJavaEmail?, PrimitiveJavaEmail?, Required, Required> = requiredReadType(",
          "storage = SqlStorage.LONG",
          "encode = PrimitiveJavaEmailTransformer::emailToLong",
          "decode = PrimitiveJavaEmailTransformer::longToEmail",
          "encodingNullness = Required"
        )
      ),
      DescriptorCase(
        label = "guards platform-null Java serializer results",
        sources = listOf(
          emailValueType(className = "PlatformJavaEmail"),
          javaTransformerSource(
            name = "JavaEmailTransformer",
            contents = """
              public final class JavaEmailTransformer {
                @ObjectToDbValue
                public static String emailToString(PlatformJavaEmail email) {
                  return email.getValue();
                }

                @DbValueToObject
                public static PlatformJavaEmail stringToEmail(String value) {
                  return new PlatformJavaEmail(value);
                }
              }
              """
          )
        ),
        descriptorFile = "PlatformJavaEmailSqlType.kt",
        expected = listOf(
          "EncodingType<PlatformJavaEmail?, PlatformJavaEmail?, SqlNullable, SqlNullable> = nullableReadType(",
          "storage = SqlStorage.STRING",
          "encode = JavaEmailTransformer::emailToString",
          "decode = JavaEmailTransformer::stringToEmail",
          "encodingNullness = SqlNullable"
        )
      )
    )

    private val GENERATED by lazy {
      SqliteMagicCompilation
        .compile(
          *DESCRIPTOR_CASES
            .flatMap(DescriptorCase::sources)
            .toTypedArray(),
          processingStepsFactory = ::transformerCodeGenerationProcessingSteps
        )
        .generatedSources()
    }

    @JvmStatic
    fun descriptorCases() = DESCRIPTOR_CASES
  }
}
