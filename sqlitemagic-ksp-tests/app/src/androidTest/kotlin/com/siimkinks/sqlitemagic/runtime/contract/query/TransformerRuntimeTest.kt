package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class TransformerRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun transformedValuesRoundTripThroughExecuteAndOneShotObservation() {
    val nonNullValue = TransformerModelCatalog.runtimeCase
      .newValue(sequence = 1)
    val nullValue = TransformerModelCatalog.runtimeCase
      .newValue(sequence = 2)
      .copy(nullableToken = null)
    val expected = listOf(
      insertValue(nonNullValue),
      insertValue(nullValue)
    )

    assertThat(
      Select
        .from(TRANSFORMER_RUNTIME_ENTITY)
        .orderBy(TRANSFORMER_RUNTIME_ENTITY.ID.asc())
        .execute()
    ).isEqualTo(expected)

    assertThat(
      Select
        .from(TRANSFORMER_RUNTIME_ENTITY)
        .orderBy(TRANSFORMER_RUNTIME_ENTITY.ID.asc())
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).isEqualTo(expected)

    val nullStorage = Select
      .raw("SELECT nullable_token FROM transformer_runtime_entity WHERE id = ?")
      .from(TRANSFORMER_RUNTIME_ENTITY)
      .withArgs(checkNotNull(expected[1].id).toString())
      .execute()
      .use { cursor ->
        check(cursor.moveToFirst())
        cursor.isNull(0)
      }
    assertThat(nullStorage)
      .isTrue()
  }

  @Test
  fun transformedStorageAndExternalConstantDispatchArePreserved() {
    val value = TransformerModelCatalog.runtimeCase
      .newValue(sequence = 1)
    val expected = insertValue(value)

    val storage = Select
      .raw(
        "SELECT nullable_token, blob_token, typeof(blob_token) " +
            "FROM transformer_runtime_entity WHERE id = ?"
      )
      .from(TRANSFORMER_RUNTIME_ENTITY)
      .withArgs(checkNotNull(expected.id).toString())
      .execute()
      .use { cursor ->
        check(cursor.moveToFirst())
        Triple(
          first = cursor.isNull(0),
          second = cursor
            .getBlob(1)
            .toList(),
          third = cursor.getString(2)
        )
      }

    assertThat(storage)
      .isEqualTo(
        Triple(
          first = false,
          second = value.blobToken.value.toList(),
          third = "blob"
        )
      )

    assertThat(
      Select
        .column(Select.asColumn(value.externalToken))
        .from(TRANSFORMER_RUNTIME_ENTITY)
        .takeFirst()
        .execute()
    ).isEqualTo(value.externalToken)
  }

  private fun insertValue(value: TransformerRuntimeEntity) = when (
    val result = value
      .insert()
      .execute()
  ) {
    is EntityInsertResult.Inserted -> value.copy(id = checkNotNull(result.rowId))
    EntityInsertResult.Ignored -> error("TransformerRuntimeEntity insert was ignored")
  }
}
