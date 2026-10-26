package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test

internal class TransformerRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun nullableTransformerRoundTripsThroughExecuteAndOneShotObservation() {
    val value = TransformerModelCatalog.runtimeCase
      .newValue(sequence = 1)
      .copy(nullableToken = null)
    val expected = TransformerModelCatalog.insertRuntimeValue(value)

    assertThat(
      SELECT
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .ORDER_BY(TRANSFORMER_RUNTIME_ENTITY.ID.ASC())
        .execute()
    ).isEqualTo(listOf(expected))

    assertThat(
      SELECT
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .ORDER_BY(TRANSFORMER_RUNTIME_ENTITY.ID.ASC())
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).isEqualTo(listOf(expected))

    val nullStorage = SELECT
      .RAW("SELECT nullable_token FROM transformer_runtime_entity WHERE id = ?", checkNotNull(expected.id))
      .toCursor()
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
    val expected = TransformerModelCatalog.insertRuntimeValue(value)

    val storage = SELECT
      .RAW(
        "SELECT nullable_token, blob_token, typeof(blob_token) " +
            "FROM transformer_runtime_entity WHERE id = ?",
        checkNotNull(expected.id)
      )
      .toCursor()
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
      SELECT
        .COLUMN(value.externalToken.asValue())
        .FROM(TRANSFORMER_RUNTIME_ENTITY)
        .takeFirst()
        .execute()
    ).isEqualTo(value.externalToken)
  }
}
