package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.contract.query.readTypedCursor
import com.siimkinks.sqlitemagic.runtime.model.ViewReadCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ViewIntegrationCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val DATABASE_NAME = "view-readback-runtime.db"

@RunWith(Parameterized::class)
class ViewReadbackRuntimeTest(
  private val viewCase: ViewReadCase<*>
) : RuntimeDatabaseTest() {
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
  fun countReturnsOneRow() = withSeededViewCase(
    viewCase = viewCase,
    assertion = ::assertCount
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
      assertSeedInserted(
        result = viewCase.insertSource(connection = connection),
        modelName = viewCase.sourceModelName
      )
      assertion(viewCase, connection)
    }
  }

  private fun <T> assertList(viewCase: ViewReadCase<T>, connection: DbConnection) {
    assertThat(
      Select
        .from(viewCase.table)
        .usingConnection(connection)
        .execute()
    ).containsExactly(viewCase.expected)
  }

  private fun <T> assertFirst(viewCase: ViewReadCase<T>, connection: DbConnection) {
    assertThat(
      Select
        .from(viewCase.table)
        .usingConnection(connection)
        .takeFirst()
        .execute()
    ).isEqualTo(viewCase.expected)
  }

  private fun <T> assertCount(viewCase: ViewReadCase<T>, connection: DbConnection) {
    assertThat(
      Select
        .from(viewCase.table)
        .usingConnection(connection)
        .count()
        .execute()
    ).isEqualTo(1L)
  }

  private fun <T> assertTypedCursor(viewCase: ViewReadCase<T>, connection: DbConnection) {
    val cursorQuery = Select
      .from(viewCase.table)
      .usingConnection(connection)
      .toCursor()
    assertThat(
      readTypedCursor(
        cursorSelect = cursorQuery,
        cursor = cursorQuery.execute()
      )
    ).containsExactly(viewCase.expected)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun viewReadCases() = ViewIntegrationCatalog.readCases
  }
}
