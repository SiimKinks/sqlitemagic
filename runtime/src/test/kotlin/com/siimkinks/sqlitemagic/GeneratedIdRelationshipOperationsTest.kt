package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class GeneratedIdRelationshipOperationsTest {
  @Test
  fun `generated relationship ids reach fixed and null omitting binders`() {
    val fixed = generatedIdAdapters()
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
    assertThat(fixedConnection.recordingDatabase.compiledStatements.last().bindings[2]).isEqualTo(41L)

    val nullOmitting = generatedIdAdapters()
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
  fun `generated relationship ids are isolated across bulk reuse and subscriptions`() {
    val bulk = generatedIdAdapters()
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

    val reused = generatedIdAdapters()
    val reusedConnection = newRecursiveConnection()
    reusedConnection.recordingDatabase.insertResults.addAll(listOf(81L, 82L, 91L, 92L))
    val builder = reused.parent
      .insert(generatedIdParent())
      .usingConnection(reusedConnection.connection)
    assertThat(builder.execute()).isEqualTo(EntityInsertResult.Inserted(82L))
    assertThat(builder.execute()).isEqualTo(EntityInsertResult.Inserted(92L))
    assertThat(reused.state.fixedChildIds)
      .containsExactly(81L, 91L)
      .inOrder()

    val cold = generatedIdAdapters()
    val coldConnection = newRecursiveConnection()
    coldConnection.recordingDatabase.insertResults.addAll(listOf(101L, 102L, 111L, 112L))
    val terminal = cold.parent
      .insert(generatedIdParent())
      .usingConnection(coldConnection.connection)
      .observe()
    assertThat(coldConnection.recordingDatabase.compiledStatements).isEmpty()
    terminal
      .test()
      .assertResult(EntityInsertResult.Inserted(rowId = 102L))
    terminal
      .test()
      .assertResult(EntityInsertResult.Inserted(rowId = 112L))
    assertThat(cold.state.fixedChildIds)
      .containsExactly(101L, 111L)
      .inOrder()
  }
}
