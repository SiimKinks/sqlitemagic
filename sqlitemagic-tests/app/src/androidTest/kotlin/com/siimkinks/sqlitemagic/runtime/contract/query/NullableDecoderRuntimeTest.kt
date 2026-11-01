package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.NullableToken
import com.siimkinks.sqlitemagic.fixture.model.OwnedToken
import com.siimkinks.sqlitemagic.fixture.model.TransformerRuntimeEntity
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog.insertRuntimeValue
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog.runtimeCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.withDefaultWriter
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

internal class NullableDecoderRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun requiredDecoderNullResultIdentifiesTransformerAndProperty() {
    insertRuntimeValue(
      runtimeCase
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
    val inserted = insertRuntimeValue(
      runtimeCase
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
  fun requiredDecodedColumnRejectsSqlNullWhileNullablePropertyStaysNull() {
    val inserted = insertRuntimeValue(
      runtimeCase
        .newValue(sequence = 1)
        .copy(nullableToken = null)
    )
    val failure = assertConstraintFailure {
      withDefaultWriter { connection ->
        connection
          .prepare("UPDATE transformer_runtime_entity SET owned_token = NULL WHERE id = ?")
          .use { statement ->
            statement.bindLong(
              index = 1,
              value = checkNotNull(inserted.id)
            )
            statement.step()
          }
      }
    }

    assertWithMessage("update")
      .that(failure)
      .hasMessageThat()
      .contains("NOT NULL constraint failed: transformer_runtime_entity.owned_token")
    ReadTerminal.entries.forEach { terminal ->
      assertWithMessage(terminal.name)
        .that(readRows(terminal))
        .isEqualTo(listOf(inserted))
    }
  }

  private fun readRows(terminal: ReadTerminal) = when (terminal) {
    ReadTerminal.EXECUTE -> SELECT.FROM(TRANSFORMER_RUNTIME_ENTITY)
      .execute()
    ReadTerminal.OBSERVE -> SELECT.FROM(TRANSFORMER_RUNTIME_ENTITY)
      .observe()
      .runQueryOnce()
      .blockingGet()
    ReadTerminal.USE_ROWS -> SELECT.FROM(TRANSFORMER_RUNTIME_ENTITY)
      .useRows(Sequence<TransformerRuntimeEntity>::toList)
  }

  private enum class ReadTerminal {
    EXECUTE,
    OBSERVE,
    USE_ROWS
  }
}
