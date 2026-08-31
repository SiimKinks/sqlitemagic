package com.siimkinks.sqlitemagic.runtime.contract.identity

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntityTable.Companion.NO_ID_MULTI_UNIQUE_ENTITY
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.NoIdMultiUniqueEntity
import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntitys
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.update
import org.junit.Test

class NoIdMultiUniqueRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun singleUpdateSupportsEachUniqueKey() {
    assertSingleUpdate(key = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
    clearDatabase()
    assertSingleUpdate(key = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
    clearDatabase()
    assertSingleUpdate(key = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
  }

  @Test
  fun bulkUpdateSupportsEachUniqueKey() {
    assertBulkUpdate(key = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
    clearDatabase()
    assertBulkUpdate(key = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
    clearDatabase()
    assertBulkUpdate(key = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
  }

  @Test
  fun singlePersistSupportsEachUniqueKey() {
    assertSinglePersist(key = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
    clearDatabase()
    assertSinglePersist(key = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
    clearDatabase()
    assertSinglePersist(key = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
  }

  @Test
  fun bulkPersistSupportsEachUniqueKey() {
    assertBulkPersist(key = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
    clearDatabase()
    assertBulkPersist(key = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
    clearDatabase()
    assertBulkPersist(key = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
  }

  @Test
  fun singleDeleteSupportsEachUniqueKey() {
    assertSingleDelete(key = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
    clearDatabase()
    assertSingleDelete(key = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
    clearDatabase()
    assertSingleDelete(key = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
  }

  @Test
  fun bulkDeleteSupportsEachUniqueKey() {
    assertBulkDelete(key = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
    clearDatabase()
    assertBulkDelete(key = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
    clearDatabase()
    assertBulkDelete(key = NO_ID_MULTI_UNIQUE_ENTITY.`as`("multi_alias").SLUG)
  }

  private fun <C> assertSingleUpdate(
    key: C
  ) where C : Column<*, *, *, NoIdMultiUniqueEntity, NotNullable>,
          C : Unique<NotNullable> {
    val seeded = values(
      prefix = "single-update",
      count = 3
    )
    seed(values = seeded)
    val executeUpdated = updatedValue(
      value = seeded[0],
      label = "execute-update"
    )
    val observeUpdated = updatedValue(
      value = seeded[1],
      label = "observe-update"
    )

    assertThat(
      executeUpdated
        .update()
        .execute(byColumn = key)
    ).isTrue()
    observeUpdated
      .update()
      .observe(byColumn = key)
      .blockingAwait()

    assertRows(
      expected = listOf(
        executeUpdated,
        observeUpdated,
        seeded[2]
      )
    )
  }

  private fun <C> assertBulkUpdate(
    key: C
  ) where C : Column<*, *, *, NoIdMultiUniqueEntity, NotNullable>,
          C : Unique<NotNullable> {
    val seeded = values(
      prefix = "bulk-update",
      count = 5
    )
    seed(values = seeded)
    val executeUpdated = updatedValues(
      values = seeded.take(2),
      label = "execute-bulk-update"
    )
    val observeUpdated = updatedValues(
      values = seeded
        .drop(2)
        .take(2),
      label = "observe-bulk-update"
    )

    assertThat(
      NoIdMultiUniqueEntitys
        .update(o = executeUpdated)
        .execute(byColumn = key)
    ).isTrue()
    NoIdMultiUniqueEntitys
      .update(o = observeUpdated)
      .observe(byColumn = key)
      .blockingAwait()

    assertRows(
      expected = executeUpdated + observeUpdated + seeded[4]
    )
  }

  private fun <C> assertSinglePersist(
    key: C
  ) where C : Column<*, *, *, NoIdMultiUniqueEntity, NotNullable>,
          C : Unique<NotNullable> {
    val seeded = values(
      prefix = "single-persist",
      count = 3
    )
    seed(values = seeded)
    val executeUpdated = updatedValue(
      value = seeded[0],
      label = "execute-persist"
    )
    val observeUpdated = updatedValue(
      value = seeded[1],
      label = "observe-persist"
    )

    assertThat(
      executeUpdated
        .persist()
        .execute(byColumn = key)
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(
      observeUpdated
        .persist()
        .observe(byColumn = key)
        .blockingGet()
    ).isEqualTo(EntityPersistResult.Updated)

    assertRows(
      expected = listOf(
        executeUpdated,
        observeUpdated,
        seeded[2]
      )
    )
  }

  private fun <C> assertBulkPersist(
    key: C
  ) where C : Column<*, *, *, NoIdMultiUniqueEntity, NotNullable>,
          C : Unique<NotNullable> {
    val seeded = values(
      prefix = "bulk-persist",
      count = 5
    )
    seed(values = seeded)
    val executeUpdated = updatedValues(
      values = seeded.take(2),
      label = "execute-bulk-persist"
    )
    val observeUpdated = updatedValues(
      values = seeded
        .drop(2)
        .take(2),
      label = "observe-bulk-persist"
    )

    assertThat(
      NoIdMultiUniqueEntitys
        .persist(o = executeUpdated)
        .execute(byColumn = key)
    ).isTrue()
    NoIdMultiUniqueEntitys
      .persist(o = observeUpdated)
      .observe(byColumn = key)
      .blockingAwait()

    assertRows(
      expected = executeUpdated + observeUpdated + seeded[4]
    )
  }

  private fun <C> assertSingleDelete(
    key: C
  ) where C : Column<*, *, *, NoIdMultiUniqueEntity, NotNullable>,
          C : Unique<NotNullable> {
    val seeded = values(
      prefix = "single-delete",
      count = 3
    )
    seed(values = seeded)

    assertThat(
      seeded[0]
        .delete()
        .execute(byColumn = key)
    ).isEqualTo(1)
    assertThat(
      seeded[1]
        .delete()
        .observe(byColumn = key)
        .blockingGet()
    ).isEqualTo(1)

    assertRows(expected = listOf(seeded[2]))
  }

  private fun <C> assertBulkDelete(
    key: C
  ) where C : Column<*, *, *, NoIdMultiUniqueEntity, NotNullable>,
          C : Unique<NotNullable> {
    val seeded = values(
      prefix = "bulk-delete",
      count = 5
    )
    seed(values = seeded)
    val executeDeleted = seeded.take(2)
    val observeDeleted = seeded
      .drop(2)
      .take(2)

    assertThat(
      NoIdMultiUniqueEntitys
        .delete(o = executeDeleted)
        .execute(byColumn = key)
    ).isEqualTo(2)
    assertThat(
      NoIdMultiUniqueEntitys
        .delete(o = observeDeleted)
        .observe(byColumn = key)
        .blockingGet()
    ).isEqualTo(2)

    assertRows(expected = listOf(seeded[4]))
  }

  private fun seed(values: List<NoIdMultiUniqueEntity>) {
    assertThat(
      NoIdMultiUniqueEntitys
        .insert(o = values)
        .execute()
    ).isTrue()
  }

  private fun values(
    prefix: String,
    count: Int
  ) = List(
    size = count,
    init = { index ->
      NoIdMultiUniqueEntity(
        slug = "$prefix-slug-$index",
        externalKey = "$prefix-external-key-$index",
        value = "$prefix-value-$index"
      )
    }
  )

  private fun updatedValue(
    value: NoIdMultiUniqueEntity,
    label: String
  ) = value.copy(value = "$label-${value.value}")

  private fun updatedValues(
    values: List<NoIdMultiUniqueEntity>,
    label: String
  ) = values.map { value ->
    updatedValue(
      value = value,
      label = label
    )
  }

  private fun assertRows(expected: List<NoIdMultiUniqueEntity>) = assertThat(
    Select
      .from(NO_ID_MULTI_UNIQUE_ENTITY)
      .execute()
  ).containsExactlyElementsIn(expected)
}
