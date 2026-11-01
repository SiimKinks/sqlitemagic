package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class GeneratedIdRelationshipOperationsTest {
  @Test
  fun `generated relationship ids reach fixed and null omitting binders`() {
    val fixed = GeneratedIdAdapters()
    val fixedConnection = newRecursiveConnection()
    fixedConnection.sqliteConnection.insertResults += listOf(41L, 42L)
    val fixedEntity = generatedIdParent()

    assertThat(
      fixed.parent
        .insert(fixedEntity)
        .usingConnection(fixedConnection.dbConnection)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(42L))
    assertThat(fixed.state.fixedChildIds).containsExactly(41L)

    val nullOmitting = GeneratedIdAdapters()
    val nullOmittingConnection = newRecursiveConnection()
    nullOmittingConnection.sqliteConnection.updateResults += 0
    nullOmittingConnection.sqliteConnection.insertResults += listOf(51L, 52L)
    assertThat(
      nullOmitting.parent
        .persist(fixedEntity)
        .usingConnection(nullOmittingConnection.dbConnection)
        .ignoreNullValues()
        .execute()
    ).isEqualTo(EntityPersistResult.Inserted(52L))
    assertThat(nullOmitting.state.nullOmittingChildIds).containsExactly(51L)
  }

  @Test
  fun `generated relationship ids reach the update of an existing parent persist`() {
    listOf(
      "fixed" to false,
      "null omitting" to true
    ).forEach { (label, ignoreNullValues) ->
      val adapters = GeneratedIdAdapters()
      val connection = newRecursiveConnection()
      connection.sqliteConnection.insertResults += 81L
      val persist = adapters.parent
        .persist(generatedIdParent())
        .usingConnection(connection.dbConnection)
      val result = when {
        ignoreNullValues -> persist.ignoreNullValues()
        else -> persist
      }.execute()

      assertWithMessage(label)
        .that(result to adapters.state.updatedChildIds)
        .isEqualTo(EntityPersistResult.Updated to listOf(81L))
    }
  }

  @Test
  fun `generated relationship ids reach the update of each parent of a bulk persist`() {
    val adapters = GeneratedIdAdapters()
    val connection = newRecursiveConnection()
    connection.sqliteConnection.insertResults += listOf(91L, 92L)
    val entities = listOf(
      generatedIdParent(parentId = "parent-1"),
      generatedIdParent(parentId = "parent-2")
    )

    assertThat(
      adapters.parent
        .bulkPersist(entities)
        .usingConnection(connection.dbConnection)
        .execute()
    ).isTrue()
    assertThat(adapters.state.updatedChildIds)
      .containsExactly(91L, 92L)
      .inOrder()
  }

  @Test
  fun `generated relationship ids reach each entity of a bulk insert`() {
    val bulk = GeneratedIdAdapters()
    val bulkConnection = newRecursiveConnection()
    bulkConnection.sqliteConnection.insertResults += listOf(61L, 62L, 71L, 72L)
    val entities = listOf(
      generatedIdParent(parentId = "parent-1"),
      generatedIdParent(parentId = "parent-2")
    )
    assertThat(
      bulk.parent
        .bulkInsert(entities)
        .usingConnection(bulkConnection.dbConnection)
        .execute()
    ).isTrue()
    assertThat(bulk.state.fixedChildIds)
      .containsExactly(61L, 71L)
      .inOrder()
  }
}
