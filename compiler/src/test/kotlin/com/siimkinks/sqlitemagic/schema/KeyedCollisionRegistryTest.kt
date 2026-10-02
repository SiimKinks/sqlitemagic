package com.siimkinks.sqlitemagic.schema

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class KeyedCollisionRegistryTest {
  @Test
  fun `unregister removes a matching owner`() {
    val registry = KeyedCollisionRegistry<String, String>()
    registry.register(
      key = "name",
      value = "owner"
    )

    assertThat(
      registry.unregister(
        key = "name",
        value = "owner"
      )
    ).isTrue()
    assertThat(registry.lookup("name"))
      .isNull()
  }

  @Test
  fun `unregister preserves a different owner for the same key`() {
    val registry = KeyedCollisionRegistry<String, String>()
    registry.register(
      key = "name",
      value = "current"
    )

    assertThat(
      registry.unregister(
        key = "name",
        value = "stale"
      )
    ).isFalse()
    assertThat(registry.lookup("name"))
      .isEqualTo("current")
  }
}
