package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.NullableToken
import com.siimkinks.sqlitemagic.fixture.model.OwnedToken
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

class NullableDecoderRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun requiredDecoderNullResultIdentifiesTransformerAndProperty() {
    insertValue(
      TransformerModelCatalog.runtimeCase
        .newValue(sequence = 1)
        .copy(ownedToken = OwnedToken("reject"))
    )

    ReadTerminal.entries.forEach { terminal ->
      val failure = assertThrows(IllegalStateException::class.java) {
        readRows(terminal)
      }

      assertWithMessage(terminal.name)
        .that(failure)
        .hasMessageThat()
        .isEqualTo(
          "Transformer com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeOwner.dbToOwnedToken " +
              "returned null for required property ownedToken"
        )
    }
  }

  @Test
  fun nullableDecoderNullResultIsAccepted() {
    val inserted = insertValue(
      TransformerModelCatalog.runtimeCase
        .newValue(sequence = 1)
        .copy(nullableToken = NullableToken("reject"))
    )
    val expected = inserted.copy(nullableToken = null)

    ReadTerminal.entries.forEach { terminal ->
      assertWithMessage(terminal.name)
        .that(readRows(terminal))
        .isEqualTo(listOf(expected))
    }
  }

  @Test
  fun actualSqlNullInputCanDecodeIntoRequiredSentinelWhileNullablePropertyStaysNull() {
    val inserted = insertValue(
      TransformerModelCatalog.runtimeCase
        .newValue(sequence = 1)
        .copy(nullableToken = null)
    )
    val expected = inserted.copy(ownedToken = OwnedToken("sentinel"))
    val typedQuery = Select
      .from(TRANSFORMER_RUNTIME_ENTITY)
      .toCursor()
    val actual = Select
      .raw(
        "SELECT id, nullable_token, blob_token, parameterized_tokens, NULL AS owned_token, " +
            "external_token, embedded_owned_token " +
            "FROM transformer_runtime_entity WHERE id = ?"
      )
      .from(TRANSFORMER_RUNTIME_ENTITY)
      .withArgs(checkNotNull(inserted.id).toString())
      .execute()
      .use { cursor ->
        check(cursor.moveToFirst())
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("owned_token")))
          .isTrue()
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("nullable_token")))
          .isTrue()
        typedQuery.getFromCurrentPosition(cursor)
      }

    assertThat(actual)
      .isEqualTo(expected)
  }

  private fun readRows(terminal: ReadTerminal) = when (terminal) {
    ReadTerminal.EXECUTE -> Select
      .from(TRANSFORMER_RUNTIME_ENTITY)
      .execute()
    ReadTerminal.OBSERVE -> Select
      .from(TRANSFORMER_RUNTIME_ENTITY)
      .observe()
      .runQueryOnce()
      .blockingGet()
    ReadTerminal.TYPED_CURSOR -> {
      val query = Select
        .from(TRANSFORMER_RUNTIME_ENTITY)
        .toCursor()
      query.execute()
        .use { cursor ->
          check(cursor.moveToFirst())
          listOf(checkNotNull(query.getFromCurrentPosition(cursor)))
        }
    }
  }

  private fun insertValue(value: TransformerRuntimeEntity) = when (
    val result = value
      .insert()
      .execute()
  ) {
    is EntityInsertResult.Inserted -> value.copy(id = checkNotNull(result.rowId))
    EntityInsertResult.Ignored -> error("TransformerRuntimeEntity insert was ignored")
  }

  private enum class ReadTerminal {
    EXECUTE,
    OBSERVE,
    TYPED_CURSOR
  }
}
