package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TransformerRuntimeEntityTable.Companion.TRANSFORMER_RUNTIME_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.NullableToken
import com.siimkinks.sqlitemagic.fixture.model.OwnedToken
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.defaultWritableDatabase
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

internal class NullableDecoderRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun requiredDecoderNullResultIdentifiesTransformerAndProperty() {
    TransformerModelCatalog.insertRuntimeValue(
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
    val inserted = TransformerModelCatalog.insertRuntimeValue(
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
  fun requiredDecodedColumnRejectsSqlNullWhileNullablePropertyStaysNull() {
    val inserted = TransformerModelCatalog.insertRuntimeValue(
      TransformerModelCatalog.runtimeCase
        .newValue(sequence = 1)
        .copy(nullableToken = null)
    )
    val failure = assertThrows(SQLiteConstraintException::class.java) {
      defaultWritableDatabase().execSQL(
        sql = "UPDATE transformer_runtime_entity SET owned_token = NULL WHERE id = ?",
        bindArgs = arrayOf(checkNotNull(inserted.id))
      )
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
    ReadTerminal.TYPED_CURSOR -> readTypedCursor(
      cursorSelect = SELECT.FROM(TRANSFORMER_RUNTIME_ENTITY)
        .toCursor()
    )
  }

  private enum class ReadTerminal {
    EXECUTE,
    OBSERVE,
    TYPED_CURSOR
  }
}
