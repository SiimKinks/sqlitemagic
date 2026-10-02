package com.siimkinks.sqlitemagic.runtime.contract.query.connection

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledCountSelect
import com.siimkinks.sqlitemagic.CompiledCursorSelect
import com.siimkinks.sqlitemagic.CompiledFirstSelect
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.runtime.contract.query.readTypedCursor
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CompiledSelectConnectionRuntimeTest(
  private val variant: SelectVariant<*>
) {
  @Before
  fun setUp() = resetCompiledSelectDatabases()

  @After
  fun tearDown() = resetCompiledSelectDatabases()

  @Test
  fun defaultSynchronousTerminals() = assertDefaultSynchronous(variant)

  @Test
  fun defaultOneShotObservedTerminals() = assertDefaultObserved(variant)

  @Test
  fun explicitConnectionSynchronousTerminals() = assertExplicitSynchronous(variant)

  @Test
  fun explicitConnectionOneShotObservedTerminals() = assertExplicitObserved(variant)

  private fun <T> assertDefaultSynchronous(variant: SelectVariant<T>) {
    val terminals = variant
      .compile(connection = null)
      .terminals()
    val expected = variant.expected(insertCompiledSelectEntity(value = "default"))
    assertSynchronous(
      terminals = terminals,
      expected = expected
    )
  }

  private fun <T> assertDefaultObserved(variant: SelectVariant<T>) {
    val terminals = variant
      .compile(connection = null)
      .terminals()
    val expected = variant.expected(insertCompiledSelectEntity(value = "default"))
    assertObserved(
      terminals = terminals,
      expected = expected
    )
  }

  private fun <T> assertExplicitSynchronous(variant: SelectVariant<T>) = withExplicitConnection(
    variant = variant,
    assertion = ::assertSynchronous
  )

  private fun <T> assertExplicitObserved(variant: SelectVariant<T>) = withExplicitConnection(
    variant = variant,
    assertion = ::assertObserved
  )

  private fun <T> withExplicitConnection(
    variant: SelectVariant<T>,
    assertion: (CompiledTerminals<T>, List<T>) -> Unit
  ) = withNamedDatabase(databaseName = ALTERNATE_DATABASE_NAME) { application ->
    openNamedConnection(
      application = application,
      databaseName = ALTERNATE_DATABASE_NAME
    ).use { alternate ->
      val expected = variant.expected(
        insertCompiledSelectEntity(
          value = "alternate",
          connection = alternate
        )
      )
      val terminals = variant
        .compile(connection = alternate)
        .terminals()
      insertCompiledSelectEntity(value = "default")
      assertion(terminals, expected)
    }
  }

  private fun <T> assertSynchronous(terminals: CompiledTerminals<T>, expected: List<T>) {
    assertThat(terminals.main.execute()).isEqualTo(expected)
    assertThat(terminals.first.execute()).isEqualTo(expected.single())
    assertThat(terminals.count.execute()).isEqualTo(1L)
    assertThat(
      readTypedCursor(
        cursorSelect = terminals.cursor,
        cursor = terminals.cursor.execute()
      )
    ).isEqualTo(expected)
  }

  private fun <T> assertObserved(terminals: CompiledTerminals<T>, expected: List<T>) {
    terminals.main
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
    terminals.first
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected.single())
    terminals.count
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(1L)
    val cursorObserver = terminals.cursor
      .observe()
      .runQueryOnce()
      .test()
    val cursor = cursorObserver
      .values()
      .single()
    assertThat(
      readTypedCursor(
        cursorSelect = terminals.cursor,
        cursor = cursor
      )
    ).isEqualTo(expected)
    cursorObserver.assertComplete()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun selectVariants(): List<SelectVariant<*>> = listOf(MultiColumn, SingleColumn)
  }
}

sealed class SelectVariant<T>(private val name: String) {
  abstract fun compile(connection: DbConnection?): CompiledSelect<T, *>
  abstract fun expected(entity: SimpleMutableEntity): List<T>
  override fun toString() = name
}

private object MultiColumn : SelectVariant<SimpleMutableEntity>(name = "multi-column model") {
  override fun compile(connection: DbConnection?): CompiledSelect<SimpleMutableEntity, *> {
    val select = Select.from(SIMPLE_MUTABLE_ENTITY)
    if (connection != null) select.usingConnection(connection)
    return select.compile()
  }

  override fun expected(entity: SimpleMutableEntity) = listOf(entity)
}

private object SingleColumn : SelectVariant<String?>(name = "single-column scalar") {
  override fun compile(connection: DbConnection?): CompiledSelect<String?, *> {
    val select = Select
      .column(SIMPLE_MUTABLE_ENTITY.VALUE)
      .from(SIMPLE_MUTABLE_ENTITY)
    if (connection != null) select.usingConnection(connection)
    return select.compile()
  }

  override fun expected(entity: SimpleMutableEntity) = listOf(checkNotNull(entity.value))
}

private data class CompiledTerminals<T>(
  val main: CompiledSelect<T, *>,
  val first: CompiledFirstSelect<T, *>,
  val count: CompiledCountSelect<*>,
  val cursor: CompiledCursorSelect<T, *>
)

private fun <T> CompiledSelect<T, *>.terminals() = CompiledTerminals(
  main = this,
  first = takeFirst(),
  count = count(),
  cursor = toCursor()
)
