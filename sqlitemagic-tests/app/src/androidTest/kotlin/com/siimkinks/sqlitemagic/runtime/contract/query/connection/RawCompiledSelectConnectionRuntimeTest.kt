package com.siimkinks.sqlitemagic.runtime.contract.query.connection

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledObservableRawSelect
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test

class RawCompiledSelectConnectionRuntimeTest {
  @Before
  fun setUp() = resetCompiledSelectDatabases()

  @After
  fun tearDown() = resetCompiledSelectDatabases()

  @Test
  fun defaultSynchronousRawSelect() {
    val compiled = compileRaw(connection = null)
    val expected = insertCompiledSelectEntity(value = "default")
    assertThat(readRawValues(compiled.execute())).containsExactly(expected.value)
  }

  @Test
  fun defaultObservedRawSelect() {
    val compiled = compileRaw(connection = null)
    val expected = insertCompiledSelectEntity(value = "default")
    val observer = compiled
      .observe()
      .runQueryOnce()
      .test()
    val cursor = observer
      .values()
      .single()
    assertThat(readRawValues(cursor)).containsExactly(expected.value)
    observer.assertComplete()
  }

  @Test
  fun explicitSynchronousRawSelect() = withAlternateRaw { compiled, expected ->
    assertThat(readRawValues(compiled.execute())).containsExactly(expected)
  }

  @Test
  fun explicitObservedRawSelect() = withAlternateRaw { compiled, expected ->
    val observer = compiled
      .observe()
      .runQueryOnce()
      .test()
    val cursor = observer
      .values()
      .single()
    assertThat(readRawValues(cursor)).containsExactly(expected)
    observer.assertComplete()
  }

  private fun withAlternateRaw(assertion: (CompiledObservableRawSelect, String) -> Unit) =
    withNamedDatabase(databaseName = ALTERNATE_DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = ALTERNATE_DATABASE_NAME
      )
        .use { alternate ->
          val expected = insertCompiledSelectEntity(
            value = "alternate",
            connection = alternate
          )
          val compiled = compileRaw(connection = alternate)
          insertCompiledSelectEntity(value = "default")
          assertion(compiled, checkNotNull(expected.value))
        }
    }

  private fun compileRaw(connection: DbConnection?) = Select
    .raw("SELECT value FROM simple_mutable_entity ORDER BY id")
    .from(SIMPLE_MUTABLE_ENTITY)
    .let { select ->
      if (connection != null) select.usingConnection(connection)
      select.compile()
    }

  private fun readRawValues(cursor: Cursor) = cursor.use {
    buildList {
      while (cursor.moveToNext()) {
        add(cursor.getString(0))
      }
    }
  }
}
