package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.testApplication
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import io.reactivex.Maybe
import io.reactivex.Single
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val ALTERNATE_DATABASE_NAME = "compiled-select-runtime-alternate.db"

class CompiledSelectConnectionRuntimeTest {
  private val selectVariants = SelectVariant.values().toList()

  @Before
  fun setUp() {
    resetDatabases()
  }

  @After
  fun tearDown() {
    resetDatabases()
  }

  @Test
  fun compiledSelectTerminalsUseDefaultConnection() {
    val expected = insertEntity(value = "default")
    val compiledQueries = selectVariants.map { variant ->
      buildCompiledTerminals(variant = variant)
    }

    compiledQueries.forEach { terminals ->
      assertSynchronousResults(
        terminals = terminals,
        expected = expected
      )
    }
  }

  @Test
  fun compiledSelectObservablesResolveDefaultWhenObserved() {
    val expected = insertEntity(value = "default")
    val compiledQueries = selectVariants.map { variant ->
      val terminals = buildCompiledTerminals(variant = variant)
      CompiledQuery(
        variant = variant,
        terminals = terminals
      )
    }

    compiledQueries.forEach { query ->
      val terminals = query.terminals
      assertSingleValue(
        source = terminals.main
          .observe()
          .runQueryOnce(),
        expected = expectedValues(
          variant = query.variant,
          expected = expected
        )
      )
      assertMaybeValue(
        source = terminals.first
          .observe()
          .runQueryOnce(),
        expected = expectedValues(
          variant = query.variant,
          expected = expected
        ).single()
      )
      assertMaybeValue(
        source = terminals.count
          .observe()
          .runQueryOnce(),
        expected = 1L
      )
      assertCursorValue(
        source = terminals.cursor
          .observe()
          .runQueryOnce(),
        cursorSelect = terminals.cursor,
        expected = expectedValues(
          variant = query.variant,
          expected = expected
        )
      )
    }
  }

  @Test
  fun explicitConnectionIsUsedInsteadOfDefault() = withNamedDatabase(
    databaseName = ALTERNATE_DATABASE_NAME
  ) { application ->
    openNamedConnection(
      application = application,
      databaseName = ALTERNATE_DATABASE_NAME
    ).use { alternateConnection ->
      val expected = insertEntity(
        value = "alternate",
        connection = alternateConnection
      )
      val compiledQueries = selectVariants.map { variant ->
        val terminals = buildCompiledTerminals(
          variant = variant,
          connection = alternateConnection
        )
        ExplicitQuery(
          variant = variant,
          terminals = terminals,
          observed = terminals.main
            .observe()
            .runQueryOnce()
        )
      }

      insertEntity(value = "default")
      compiledQueries.forEach { query ->
        assertSynchronousResults(
          terminals = query.terminals,
          expected = expected
        )
        assertSingleValue(
          source = query.observed,
          expected = expectedValues(
            variant = query.variant,
            expected = expected
          )
        )
      }
    }
  }

  @Test
  fun compiledRawSelectUsesDefaultAtExecutionAndObservation() {
    val expected = insertEntity(value = "default")
    val compiled = Select
      .raw("SELECT value FROM simple_mutable_entity ORDER BY id")
      .from(SIMPLE_MUTABLE_ENTITY)
      .compile()
    assertThat(readRawValues(compiled.execute())).containsExactly(expected.value)
    assertRawObservableValue(
      source = compiled
        .observe()
        .runQueryOnce(),
      expected = expected.value
    )
  }

  @Test
  fun explicitRawSelectUsesExplicitConnectionInsteadOfDefault() = withNamedDatabase(
    databaseName = ALTERNATE_DATABASE_NAME
  ) { application ->
    openNamedConnection(
      application = application,
      databaseName = ALTERNATE_DATABASE_NAME
    ).use { alternateConnection ->
      val expected = insertEntity(
        value = "alternate",
        connection = alternateConnection
      )
      val compiled = Select
        .raw("SELECT value FROM simple_mutable_entity ORDER BY id")
        .from(SIMPLE_MUTABLE_ENTITY)
        .usingConnection(alternateConnection)
        .compile()
      val observed = compiled
        .observe()
        .runQueryOnce()

      insertEntity(value = "default")
      assertThat(readRawValues(compiled.execute())).containsExactly(expected.value)
      assertRawObservableValue(
        source = observed,
        expected = expected.value
      )
    }
  }

  private fun buildCompiledTerminals(
    variant: SelectVariant,
    connection: DbConnection? = null
  ) = buildCompiledSelect(
    variant = variant,
    connection = connection
  ).let { compiled ->
    CompiledTerminals(
      variant = variant,
      main = compiled,
      first = compiled.takeFirst(),
      count = compiled.count(),
      cursor = compiled.toCursor()
    )
  }

  private fun buildCompiledSelect(
    variant: SelectVariant,
    connection: DbConnection? = null
  ): CompiledSelect<*, *> = when (variant) {
    SelectVariant.MULTI_COLUMN -> {
      val select = Select.from(SIMPLE_MUTABLE_ENTITY)
      if (connection != null) {
        select.usingConnection(connection)
      }
      select.compile()
    }
    SelectVariant.SINGLE_COLUMN -> {
      val select = Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
      if (connection != null) {
        select.usingConnection(connection)
      }
      select.compile()
    }
  }

  private fun insertEntity(
    value: String,
    connection: DbConnection? = null
  ): SimpleMutableEntity {
    val entity = SimpleMutableEntity(
      id = null,
      value = value,
      boxedBoolean = true,
      primitiveBoolean = false
    )
    val result = when (connection) {
      null -> entity
        .insert()
        .execute()
      else -> entity
        .insert()
        .usingConnection(connection)
        .execute()
    }
    return when (result) {
      is EntityInsertResult.Inserted -> entity.copy(id = checkNotNull(result.rowId))
      EntityInsertResult.Ignored -> error("Deterministic insert was ignored")
    }
  }

  private fun assertSynchronousResults(
    terminals: CompiledTerminals,
    expected: SimpleMutableEntity
  ) {
    val expectedValues = expectedValues(
      variant = terminals.variant,
      expected = expected
    )
    assertThat(terminals.main.execute()).isEqualTo(expectedValues)
    assertThat(terminals.first.execute()).isEqualTo(expectedValues.single())
    assertThat(terminals.count.execute()).isEqualTo(1L)
    assertThat(readCursor(terminals.cursor)).isEqualTo(expectedValues)
  }

  private fun assertSingleValue(
    source: Single<*>,
    expected: Any?
  ) {
    val observer = source.test()
    assertThat(observer.values()).containsExactly(expected)
    observer.assertComplete()
  }

  private fun assertMaybeValue(
    source: Maybe<*>,
    expected: Any?
  ) {
    val observer = source.test()
    assertThat(observer.values()).containsExactly(expected)
    observer.assertComplete()
  }

  private fun assertCursorValue(
    source: Maybe<*>,
    cursorSelect: CompiledCursorSelect<*, *>,
    expected: List<Any?>
  ) {
    val observer = source.test()
    assertThat(observer.values()).hasSize(1)
    val cursor = observer.values().single() as Cursor
    assertThat(readCursor(cursorSelect, cursor)).isEqualTo(expected)
    observer.assertComplete()
  }

  private fun assertRawObservableValue(
    source: Maybe<Cursor>,
    expected: String?
  ) {
    val observer = source.test()
    assertThat(observer.values()).hasSize(1)
    assertThat(readRawValues(observer.values().single())).containsExactly(expected)
    observer.assertComplete()
  }

  private fun readCursor(cursorSelect: CompiledCursorSelect<*, *>): List<Any?> = cursorSelect
    .execute()
    .use { cursor ->
      readCursor(
        cursorSelect = cursorSelect,
        cursor = cursor
      )
    }

  private fun readCursor(
    cursorSelect: CompiledCursorSelect<*, *>,
    cursor: Cursor
  ): List<Any?> = cursor.use {
    buildList {
      while (cursor.moveToNext()) {
        add(cursorSelect.getFromCurrentPosition(cursor))
      }
    }
  }

  private fun readRawValues(cursor: Cursor): List<String?> = cursor.use {
    buildList {
      while (cursor.moveToNext()) {
        add(cursor.getString(0))
      }
    }
  }

  private fun expectedValues(
    variant: SelectVariant,
    expected: SimpleMutableEntity
  ): List<Any?> = when (variant) {
    SelectVariant.MULTI_COLUMN -> listOf(expected)
    SelectVariant.SINGLE_COLUMN -> listOf(expected.value)
  }

  private fun resetDatabases() {
    val application = testApplication()
    application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    SqliteMagic.getDefaultConnection().clearData()
  }

  private enum class SelectVariant {
    MULTI_COLUMN,
    SINGLE_COLUMN
  }

  private data class CompiledQuery(
    val variant: SelectVariant,
    val terminals: CompiledTerminals
  )

  private data class CompiledTerminals(
    val variant: SelectVariant,
    val main: CompiledSelect<*, *>,
    val first: CompiledFirstSelect<*, *>,
    val count: CompiledCountSelect<*>,
    val cursor: CompiledCursorSelect<*, *>
  )

  private data class ExplicitQuery(
    val variant: SelectVariant,
    val terminals: CompiledTerminals,
    val observed: Single<*>
  )
}
