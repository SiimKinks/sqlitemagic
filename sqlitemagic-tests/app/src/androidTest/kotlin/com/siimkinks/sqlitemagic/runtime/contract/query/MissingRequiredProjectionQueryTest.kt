package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.MissingRequiredProjectionCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MissingRequiredProjectionQueryTest(
  private val modelCase: MissingRequiredProjectionCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeThrowsExpectedSQLException() {
    assertExpectedSQLException(modelCase = modelCase) {
      Select
        .columns(*modelCase.missingRequiredProjectionColumns.toTypedArray())
        .from(modelCase.table)
        .execute()
    }
  }

  @Test
  fun observeRunQueryOnceThrowsExpectedSQLException() {
    assertExpectedSQLException(modelCase = modelCase) {
      Select
        .columns(*modelCase.missingRequiredProjectionColumns.toTypedArray())
        .from(modelCase.table)
        .observe()
        .runQueryOnce()
        .blockingGet()
    }
  }

  private fun <T> assertExpectedSQLException(
    modelCase: MissingRequiredProjectionCase<T>,
    query: () -> Unit
  ) {
    seedExpectedRows(
      modelCase = modelCase,
      count = 1
    )

    val exception = assertThrows(SQLException::class.java, query)

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo(modelCase.expectedSQLExceptionMessage)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.missingRequiredProjectionCases
  }
}
