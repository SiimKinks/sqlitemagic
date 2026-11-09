package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.NullableLongKeyEntityTable.Companion.NULLABLE_LONG_KEY_ENTITY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.fixture.model.NullableLongKeyEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test

internal class NullableKeyReadRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun nullableLongKeysReadTheStoredValue() {
    listOf(
      "zero" to 0L,
      "positive" to 7L,
      "negative" to -7L,
      "largest" to Long.MAX_VALUE
    ).forEach { (label, id) ->
      val expected = NullableLongKeyEntity(
        id = id,
        name = label
      )
      expected
        .insert()
        .execute()

      assertWithMessage(label)
        .that(
          SELECT
            .FROM(NULLABLE_LONG_KEY_ENTITY)
            .WHERE(NULLABLE_LONG_KEY_ENTITY.ID IS id)
            .takeFirst()
            .execute()
        )
        .isEqualTo(expected)
    }
  }
}
