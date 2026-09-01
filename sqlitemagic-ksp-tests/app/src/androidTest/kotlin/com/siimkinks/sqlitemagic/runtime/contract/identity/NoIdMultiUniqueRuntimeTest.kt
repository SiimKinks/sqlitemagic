package com.siimkinks.sqlitemagic.runtime.contract.identity

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntityTable.Companion.NO_ID_MULTI_UNIQUE_ENTITY
import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntitys
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.fixture.model.NoIdMultiUniqueEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.update
import org.junit.Test

class NoIdMultiUniqueRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun aliasedSlugColumnUpdatesOnlySelectedRow() {
    val seeded = listOf(
      NoIdMultiUniqueEntity(
        slug = "alias-slug-0",
        externalKey = "alias-external-key-0",
        value = "alias-value-0"
      ),
      NoIdMultiUniqueEntity(
        slug = "alias-slug-1",
        externalKey = "alias-external-key-1",
        value = "alias-value-1"
      ),
      NoIdMultiUniqueEntity(
        slug = "alias-slug-2",
        externalKey = "alias-external-key-2",
        value = "alias-value-2"
      )
    )
    assertThat(
      NoIdMultiUniqueEntitys
        .insert(o = seeded)
        .execute()
    ).isTrue()
    val updated = seeded[1].copy(value = "alias-updated-value-1")

    assertThat(
      updated
        .update()
        .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
    ).isTrue()

    assertThat(
      Select
        .from(NO_ID_MULTI_UNIQUE_ENTITY)
        .execute()
    ).containsExactlyElementsIn(
      listOf(
        seeded[0],
        updated,
        seeded[2]
      )
    )
  }
}
