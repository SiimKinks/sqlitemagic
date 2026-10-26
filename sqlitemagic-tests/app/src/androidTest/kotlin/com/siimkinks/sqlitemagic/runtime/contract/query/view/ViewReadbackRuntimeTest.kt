package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.contract.query.readTypedCursor
import com.siimkinks.sqlitemagic.runtime.model.ViewReadCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ViewIntegrationCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val DATABASE_NAME = "view-readback-runtime.db"

@RunWith(Parameterized::class)
internal class ViewReadbackRuntimeTest(
  private val viewCase: ViewReadCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun listReturnsCompleteViewValue() = withSeededViewCase(
    viewCase = viewCase,
    assertion = ::assertList
  )

  @Test
  fun firstReturnsCompleteViewValue() = withSeededViewCase(
    viewCase = viewCase,
    assertion = ::assertFirst
  )

  @Test
  fun typedCursorReturnsCompleteViewValue() = withSeededViewCase(
    viewCase = viewCase,
    assertion = ::assertTypedCursor
  )

  private fun <T> withSeededViewCase(
    viewCase: ViewReadCase<T>,
    assertion: (ViewReadCase<T>, DbConnection) -> Unit
  ) = withNamedDatabase(databaseName = DATABASE_NAME) { application ->
    openNamedConnection(
      application = application,
      databaseName = DATABASE_NAME
    ).use { connection ->
      insertedResult(
        result = viewCase.insertSource(connection = connection),
        modelName = viewCase.sourceModelName
      )
      assertion(viewCase, connection)
    }
  }

  private fun <T> assertList(viewCase: ViewReadCase<T>, connection: DbConnection) {
    assertThat(
      SELECT
        .FROM(viewCase.table)
        .usingConnection(connection)
        .execute()
    ).containsExactly(viewCase.expected)
  }

  private fun <T> assertFirst(viewCase: ViewReadCase<T>, connection: DbConnection) {
    assertThat(
      SELECT
        .FROM(viewCase.table)
        .usingConnection(connection)
        .takeFirst()
        .execute()
    ).isEqualTo(viewCase.expected)
  }

  private fun <T> assertTypedCursor(viewCase: ViewReadCase<T>, connection: DbConnection) {
    val cursorQuery = SELECT
      .FROM(viewCase.table)
      .usingConnection(connection)
      .toCursor()
    assertThat(readTypedCursor(cursorSelect = cursorQuery)).containsExactly(viewCase.expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun viewReadCases() = ViewIntegrationCatalog.readCases
  }
}
