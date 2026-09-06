package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledDelete
import com.siimkinks.sqlitemagic.Delete
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.*
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class QueryBuilderDeleteTest : RuntimeDatabaseTest() {
  @Test
  fun executeRawPredicateDelete() {
    assertRawPredicateDelete(terminal = EXECUTE)
  }

  @Test
  fun observeRawPredicateDelete() {
    assertRawPredicateDelete(terminal = OBSERVE)
  }

  @Test
  fun executeTypedPredicatePublishesOnlyForDeletedRows() {
    assertTypedPredicatePublishesOnlyForDeletedRows(terminal = EXECUTE)
  }

  @Test
  fun observeTypedPredicatePublishesOnlyForDeletedRows() {
    assertTypedPredicatePublishesOnlyForDeletedRows(terminal = OBSERVE)
  }

  @Test
  fun executeTypedWholeTableDelete() {
    assertTypedWholeTableDelete(terminal = EXECUTE)
  }

  @Test
  fun observeTypedWholeTableDelete() {
    assertTypedWholeTableDelete(terminal = OBSERVE)
  }

  @Test
  fun executeRawWholeTableDelete() {
    assertRawWholeTableDelete(terminal = EXECUTE)
  }

  @Test
  fun observeRawWholeTableDelete() {
    assertRawWholeTableDelete(terminal = OBSERVE)
  }

  @Test
  fun executeAliasedTableDelete() {
    assertAliasedTableDelete(terminal = EXECUTE)
  }

  @Test
  fun observeAliasedTableDelete() {
    assertAliasedTableDelete(terminal = OBSERVE)
  }

  private fun assertRawPredicateDelete(terminal: OperationTerminal) {
    val first = simpleRow(
      id = 101L,
      value = "first"
    )
    val second = simpleRow(
      id = 102L,
      value = "second"
    )
    insert(first)
    insert(second)

    val affectedRows = execute(
      statement = Delete
        .from("simple_mutable_entity")
        .where("simple_mutable_entity.value=?", "second")
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(1)
    assertRows(first)
  }

  private fun assertTypedPredicatePublishesOnlyForDeletedRows(terminal: OperationTerminal) {
    val first = simpleRow(
      id = 201L,
      value = "first"
    )
    val second = simpleRow(
      id = 202L,
      value = "second"
    )
    insert(first)
    insert(second)
    val observer = Select
      .from(SIMPLE_MUTABLE_ENTITY)
      .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(listOf(first, second))
    try {
      val affectedRows = execute(
        statement = Delete
          .from(SIMPLE_MUTABLE_ENTITY)
          .where(SIMPLE_MUTABLE_ENTITY.ID.`is`(checkNotNull(second.id)))
          .compile(),
        terminal = terminal
      )

      assertThat(affectedRows).isEqualTo(1)
      observer.assertValuesOnly(
        listOf(first, second),
        listOf(first)
      )
      assertThat(
        execute(
          statement = Delete
            .from(SIMPLE_MUTABLE_ENTITY)
            .where(SIMPLE_MUTABLE_ENTITY.ID.`is`(checkNotNull(second.id)))
            .compile(),
          terminal = terminal
        )
      ).isEqualTo(0)
      observer.assertValuesOnly(
        listOf(first, second),
        listOf(first)
      )
      assertRows(first)
    } finally {
      observer.dispose()
    }
  }

  private fun assertTypedWholeTableDelete(terminal: OperationTerminal) {
    insert(
      simpleRow(
        id = 301L,
        value = "first"
      )
    )
    insert(
      simpleRow(
        id = 302L,
        value = "second"
      )
    )

    val affectedRows = execute(
      statement = Delete
        .from(SIMPLE_MUTABLE_ENTITY)
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(2)
    assertRows()
  }

  private fun assertRawWholeTableDelete(terminal: OperationTerminal) {
    insert(
      simpleRow(
        id = 401L,
        value = "first"
      )
    )
    insert(
      simpleRow(
        id = 402L,
        value = "second"
      )
    )

    val affectedRows = execute(
      statement = Delete
        .from("simple_mutable_entity")
        .where("1")
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(2)
    assertRows()
  }

  private fun assertAliasedTableDelete(terminal: OperationTerminal) {
    val row = simpleRow(
      id = 501L,
      value = "aliased-target"
    )
    insert(row)
    val alias = SIMPLE_MUTABLE_ENTITY.`as`("target")

    val affectedRows = execute(
      statement = Delete
        .from(alias)
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(1)
    assertRows()
  }

  private fun execute(
    statement: CompiledDelete,
    terminal: OperationTerminal
  ) = when (terminal) {
    EXECUTE -> statement.execute()
    OBSERVE -> statement.observe().blockingGet()
  }

  private fun assertRows(vararg expected: SimpleMutableEntity) {
    assertThat(
      Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
        .execute()
    ).isEqualTo(expected.toList())
  }

  private fun simpleRow(
    id: Long,
    value: String
  ) = SimpleMutableEntity(
    id = id,
    value = value,
    boxedBoolean = null,
    primitiveBoolean = false
  )

  private fun insert(value: SimpleMutableEntity) {
    assertThat(
      value
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)
  }
}
