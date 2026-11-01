package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog.insertRuntimeValue
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog.runtimeCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.withDefaultWriter
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test

internal class TransformerRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun nullableTransformerRoundTripsThroughExecuteAndOneShotObservation() {
    val value = runtimeCase
      .newValue(sequence = 1)
      .copy(nullableToken = null)
    val expected = insertRuntimeValue(value)

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

    val nullStorage = withDefaultWriter { connection ->
      connection
        .prepare("SELECT nullable_token FROM transformer_runtime_entity WHERE id = ?")
        .use { statement ->
          statement.bindLong(
            index = 1,
            value = checkNotNull(expected.id)
          )
          check(statement.step())
          statement.isNull(0)
        }
    }
    assertThat(nullStorage)
      .isTrue()
  }

  @Test
  fun transformedStorageAndExternalConstantDispatchArePreserved() {
    val value = runtimeCase
      .newValue(sequence = 1)
    val expected = insertRuntimeValue(value)

    val storage = withDefaultWriter { connection ->
      connection
        .prepare("SELECT nullable_token, blob_token, typeof(blob_token) FROM transformer_runtime_entity WHERE id = ?")
        .use { statement ->
          statement.bindLong(
            index = 1,
            value = checkNotNull(expected.id)
          )
          check(statement.step())
          Triple(
            first = statement.isNull(0),
            second = statement
              .getBlob(1)
              .toList(),
            third = statement.getText(2)
          )
        }
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
