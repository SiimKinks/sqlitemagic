package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.ExternalToken

data class NullableToken(val value: String)

@ObjectToDbValue
fun nullableTokenToDb(value: NullableToken?): String? = value?.value

@DbValueToObject
fun dbToNullableToken(value: String?): NullableToken? = value?.let(::NullableToken)

class BlobToken(val value: ByteArray) {
  override fun equals(other: Any?) = when {
    this === other -> true
    other !is BlobToken -> false
    else -> value.contentEquals(other.value)
  }

  override fun hashCode() = value.contentHashCode()
}

@ObjectToDbValue
fun blobTokenToDb(value: BlobToken): ByteArray = value.value

@DbValueToObject
fun dbToBlobToken(value: ByteArray): BlobToken = BlobToken(value)

data class ParameterizedToken(val value: String)

@ObjectToDbValue
fun parameterizedTokensToDb(value: List<ParameterizedToken>): String =
  value
    .joinToString(
      separator = "|",
      transform = ParameterizedToken::value
    )

@DbValueToObject
fun dbToParameterizedTokens(value: String): List<ParameterizedToken> = when {
  value.isEmpty() -> emptyList()
  else -> value.split('|').map(::ParameterizedToken)
}

data class OwnedToken(val value: String)

object TransformerRuntimeOwner {
  @ObjectToDbValue
  fun ownedTokenToDb(value: OwnedToken): String = value.value

  @DbValueToObject
  fun dbToOwnedToken(value: String): OwnedToken = OwnedToken(value)
}

data class TransformerRuntimeEmbedded(
  val ownedToken: OwnedToken
)

@Table
data class TransformerRuntimeEntity(
  @Id val id: Long?,
  val nullableToken: NullableToken?,
  val blobToken: BlobToken,
  val parameterizedTokens: List<ParameterizedToken>,
  val ownedToken: OwnedToken,
  val externalToken: ExternalToken,
  @Embedded(prefix = "embedded_") val embedded: TransformerRuntimeEmbedded
)
