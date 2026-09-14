package com.siimkinks.sqlitemagic.schema

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.schema.SqliteSchema.TEMPORARY
import org.junit.jupiter.api.Test

internal class SqliteSchemaIdentityTest {
  @Test
  fun `preserves raw names and applies ASCII-only normalization`() {
    val identifier = SqliteIdentifier.from("Books_ÄCCOUNT_123")

    assertThat(identifier.rawName)
      .isEqualTo("Books_ÄCCOUNT_123")
    assertThat(identifier.normalizedName)
      .isEqualTo("books_Äccount_123")
  }

  @Test
  fun `exposes a schema-aware normalized key`() {
    val identity = SqliteSchemaIdentity(
      schema = TEMPORARY,
      identifier = SqliteIdentifier.from("Books_ÄCCOUNT_123")
    )
    val normalizedKey = identity.normalizedKey

    assertThat(normalizedKey.schema)
      .isEqualTo(TEMPORARY)
    assertThat(normalizedKey.normalizedName)
      .isEqualTo("books_Äccount_123")
    assertThat(identity.normalizedKey)
      .isSameInstanceAs(normalizedKey)
  }

  @Test
  fun `delegates schema provider values through the identity`() {
    val identity = SqliteSchemaIdentity(
      schema = SqliteSchema.MAIN,
      identifier = SqliteIdentifier.from("Books_ÄCCOUNT_123")
    )
    val provider: SqliteSchemaProvider = identity

    assertThat(provider.schema)
      .isEqualTo(identity.schema)
    assertThat(provider.identifier)
      .isEqualTo(identity.identifier)
    assertThat(provider.rawName)
      .isEqualTo(identity.identifier.rawName)
    assertThat(provider.normalizedName)
      .isEqualTo(identity.identifier.normalizedName)
    assertThat(provider.normalizedKey)
      .isEqualTo(identity.normalizedKey)
  }
}
