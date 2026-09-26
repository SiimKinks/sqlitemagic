package com.siimkinks.sqlitemagic.schema

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaProvider
import org.junit.jupiter.api.Test

internal class SqliteSchemaIdentityTest {
  @Test
  fun `shared identity implements provider contract`() {
    val sharedIdentity = SqliteSchemaIdentity(
      schema = MAIN,
      identifier = SqliteIdentifier.from("Books")
    )
    val provider: SqliteSchemaProvider = sharedIdentity

    assertThat(provider.schema)
      .isEqualTo(sharedIdentity.schema)
    assertThat(provider.identifier)
      .isSameInstanceAs(sharedIdentity.identifier)
    assertThat(provider.rawName)
      .isEqualTo(sharedIdentity.rawName)
    assertThat(provider.normalizedName)
      .isEqualTo(sharedIdentity.normalizedName)
    assertThat(provider.normalizedKey)
      .isSameInstanceAs(sharedIdentity.normalizedKey)
  }
}
