package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class GeneratedIdRelationshipOperationsTest {
  @Test
  fun `generated relationship ids reach fixed and null omitting binders`() {
    val fixed = GeneratedIdAdapters()
    val fixedConnection = newRecursiveConnection()
    fixedConnection.recordingDatabase.insertResults.addAll(listOf(41L, 42L))
    val fixedEntity = generatedIdParent()

    assertThat(
      fixed.parent
        .insert(fixedEntity)
        .usingConnection(fixedConnection.connection)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(42L))
    assertThat(fixed.state.fixedChildIds).containsExactly(41L)

    val nullOmitting = GeneratedIdAdapters()
    val nullOmittingConnection = newRecursiveConnection()
    nullOmittingConnection.recordingDatabase.updateResults += 0
    nullOmittingConnection.recordingDatabase.insertResults.addAll(listOf(51L, 52L))
    assertThat(
      nullOmitting.parent
        .persist(fixedEntity)
        .usingConnection(nullOmittingConnection.connection)
        .ignoreNullValues()
        .execute()
    ).isEqualTo(EntityPersistResult.Inserted(52L))
    assertThat(nullOmitting.state.nullOmittingChildIds).containsExactly(51L)
  }

  @Test
  fun `generated relationship ids reach each entity of a bulk insert`() {
    val bulk = GeneratedIdAdapters()
    val bulkConnection = newRecursiveConnection()
    bulkConnection.recordingDatabase.insertResults.addAll(listOf(61L, 62L, 71L, 72L))
    val entities = listOf(
      generatedIdParent(parentId = "parent-1"),
      generatedIdParent(parentId = "parent-2")
    )
    assertThat(
      bulk.parent
        .bulkInsert(entities)
        .usingConnection(bulkConnection.connection)
        .execute()
    ).isTrue()
    assertThat(bulk.state.fixedChildIds)
      .containsExactly(61L, 71L)
      .inOrder()
  }
}
