package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.kotlinTransformerSource
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class TransformerSqlTypeContractTest {
  private val compiler = SqliteMagicCompiler(::transformerCodeGenerationProcessingSteps)

  @Test
  fun `generates descriptors and conversions for registered transformers without models`() {
    val generated = compiler
      .compile(
        emailValueType(),
        objectTransformer(),
        kotlinTransformerSource(
          name = "DescriptorTransformers",
          contents = """
            data class SentinelToken(val value: String)

            @ObjectToDbValue
            fun sentinelTokenToDb(value: SentinelToken): String? = when (value.value) {
              "sentinel" -> null
              else -> "encoded:" + value.value
            }

            @DbValueToObject
            fun sentinelTokenFromDb(value: String?): SentinelToken = when (value) {
              null -> SentinelToken("sentinel")
              else -> SentinelToken(value.removePrefix("encoded:"))
            }

            data class NullableToken(val value: String)

            @ObjectToDbValue
            fun nullableTokenToDb(value: NullableToken?): String = when (value) {
              null -> "absent"
              else -> "encoded:" + value.value
            }

            @DbValueToObject
            fun nullableTokenFromDb(value: String): NullableToken? = when (value) {
              "absent" -> null
              else -> NullableToken(value.removePrefix("encoded:"))
            }

            @ObjectToDbValue
            fun emailsToDb(value: List<Email>): String = value.joinToString(transform = Email::value)

            @DbValueToObject
            fun emailsFromDb(value: String): List<Email> = value.split(',').map(::Email)

            @ObjectToDbValue
            fun sentinelTokensToDb(value: List<SentinelToken>): String =
              value.joinToString(transform = SentinelToken::value)

            @DbValueToObject
            fun sentinelTokensFromDb(value: String): List<SentinelToken> = value.split(',').map(::SentinelToken)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "EmailSqlType.kt",
        "SentinelTokenSqlType.kt",
        "NullableTokenSqlType.kt",
        "List_EmailSqlType.kt",
        "List_SentinelTokenSqlType.kt"
      )
      .withGeneratedSource("EmailSqlType.kt") {
        it.assertContainsUnwrapped(
          "EncodingType<Email, Email, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = EmailTransformer::emailToString",
          "decode = EmailTransformer::stringToEmail",
          "encodingNullness = Required"
        )
      }
      .withGeneratedSource("SentinelTokenSqlType.kt") {
        it.assertContainsUnwrapped(
          "EncodingType<SentinelToken, SentinelToken, SqlNullable, SqlNullable> = nullableReadType(",
          "storage = SqlStorage.STRING",
          "encode = ::sentinelTokenToDb",
          "decode = ::sentinelTokenFromDb",
          "encodingNullness = SqlNullable"
        )
      }
      .withGeneratedSource("List_EmailSqlType.kt") {
        it.assertContainsUnwrapped(
          "EncodingType<List<Email>, List<Email>, Required, Required> = requiredReadType(",
          "storage = SqlStorage.STRING",
          "encode = ::emailsToDb",
          "decode = ::emailsFromDb",
          "encodingNullness = Required"
        )
      }

    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "DescriptorConsumer.kt",
          contents = """
            package downstream

            import $FIXTURE_PACKAGE.Email
            import $FIXTURE_PACKAGE.SentinelToken
            import $FIXTURE_PACKAGE.NullableToken
            import com.siimkinks.sqlitemagic.EMAIL
            import com.siimkinks.sqlitemagic.NULLABLE_TOKEN
            import com.siimkinks.sqlitemagic.SENTINEL_TOKEN
            import com.siimkinks.sqlitemagic.EncodedSqlExpression
            import com.siimkinks.sqlitemagic.Required
            import com.siimkinks.sqlitemagic.SqlNullability
            import com.siimkinks.sqlitemagic.SqlNullable
            import com.siimkinks.sqlitemagic.SqlType
            import com.siimkinks.sqlitemagic.asValue
            import typewitness.Exact

            fun <W, R, N : SqlNullability, T> EncodedSqlExpression<W, R, N, T>.exact() =
              Exact<EncodedSqlExpression<W, R, N, T>>()

            fun verify() {
              val email = Email("alice").asValue()
              val explicitEmail = Email("alice").asValue(SqlType.EMAIL)
              val sentinel = SentinelToken("sentinel").asValue()
              val explicitSentinel = SentinelToken("value").asValue(SqlType.SENTINEL_TOKEN)
              val nullable = NullableToken("value").asValue()
              val absent = (null as NullableToken?).asValue(SqlType.NULLABLE_TOKEN)
              val emails = listOf(Email("alice")).asValue()
              val sentinels = listOf(SentinelToken("value")).asValue()

              val emailExact = email.exact()
              val explicitEmailExact = explicitEmail.exact()
              val sentinelExact = sentinel.exact()
              val explicitSentinelExact = explicitSentinel.exact()
              val nullableExact = nullable.exact()
              val absentExact = absent.exact()
              val emailsExact = emails.exact()
              val sentinelsExact = sentinels.exact()

              val emailType: Exact<EncodedSqlExpression<Email, Email, Required, Email>> = emailExact
              val explicitEmailType: Exact<EncodedSqlExpression<Email, Email, Required, Email>> = explicitEmailExact
              val sentinelType: Exact<EncodedSqlExpression<SentinelToken, SentinelToken, SqlNullable, SentinelToken?>> =
                sentinelExact
              val explicitSentinelType:
                Exact<EncodedSqlExpression<SentinelToken, SentinelToken, SqlNullable, SentinelToken?>> =
                explicitSentinelExact
              val nullableType: Exact<EncodedSqlExpression<NullableToken, NullableToken?, Required, NullableToken?>> =
                nullableExact
              val absentType: Exact<EncodedSqlExpression<NullableToken?, NullableToken?, Required, NullableToken?>> =
                absentExact
              val emailsType: Exact<EncodedSqlExpression<List<Email>, List<Email>, Required, List<Email>>> = emailsExact
              val sentinelsType:
                Exact<EncodedSqlExpression<List<SentinelToken>, List<SentinelToken>, Required, List<SentinelToken>>> =
                sentinelsExact
            }
          """
        ),
        classpaths = listOf(generated.outputDirectory)
      )
      .isOk()

    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "WrongDescriptorConsumer.kt",
          contents = """
            package downstream.invalid

            import com.siimkinks.sqlitemagic.EMAIL
            import com.siimkinks.sqlitemagic.SqlType
            import com.siimkinks.sqlitemagic.asValue

            val invalid = "host string".asValue(SqlType.EMAIL)
          """
        ),
        classpaths = listOf(generated.outputDirectory)
      )
      .assertCompilationError("asValue")
  }
}
