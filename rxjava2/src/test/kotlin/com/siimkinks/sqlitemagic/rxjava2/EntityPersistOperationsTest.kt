package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.TestEntity
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.newConnection
import com.siimkinks.sqlitemagic.persist
import org.junit.Test

internal class EntityPersistOperationsTest {
  @Test
  fun `observed terminal snapshots null omission configuration`() {
    val connection = newConnection()
    connection.sqliteConnection.updateResults += listOf(1, 1)
    val builder = TestAdapter()
      .persist(TestEntity(name = null))
      .usingConnection(connection.dbConnection)
    val fixedValues = builder.observe()

    builder.ignoreNullValues()
    fixedValues
      .test()
      .assertResult(EntityPersistResult.Updated)
    builder
      .observe()
      .test()
      .assertResult(EntityPersistResult.Updated)

    assertThat(connection.statementSql)
      .containsExactly(
        "UPDATE books SET name=? WHERE id=?",
        """UPDATE "books" SET "id"="id" WHERE "id"=?"""
      )
      .inOrder()
  }

  @Test
  fun `each observed subscription receives isolated execution scratch state`() {
    val connection = newConnection()
    connection.sqliteConnection.updateResults += listOf(0, 0)
    connection.sqliteConnection.insertResults += listOf(7L, 8L)
    val adapter = TestAdapter()
    val persist = adapter
      .persist(TestEntity(name = null))
      .usingConnection(connection.dbConnection)
      .ignoreNullValues()
      .observe()

    persist
      .test()
      .assertResult(EntityPersistResult.Inserted(rowId = 7L))
    persist
      .test()
      .assertResult(EntityPersistResult.Inserted(rowId = 8L))

    assertThat(adapter.bindMaps).hasSize(2)
    assertThat(adapter.bindMaps[0]).isNotSameInstanceAs(adapter.bindMaps[1])
  }
}
