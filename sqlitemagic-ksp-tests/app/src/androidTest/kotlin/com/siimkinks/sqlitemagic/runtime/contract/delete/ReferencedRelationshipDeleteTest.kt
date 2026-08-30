package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.ReferencedDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import io.reactivex.Single
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReferencedRelationshipDeleteTest(
  private val modelCase: ReferencedDeleteModelCase<*, *>
) : RuntimeDatabaseTest() {
  @Test
  fun executeDeletesReferencedChild() {
    assertSingleDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeDeletesReferencedChild() {
    assertSingleDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeBulkDeletesReferencedChildren() {
    assertBulkDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeBulkDeletesReferencedChildren() {
    assertBulkDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeDeletesReferencedChildrenByTable() {
    assertTableDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeDeletesReferencedChildrenByTable() {
    assertTableDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T, R> assertSingleDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal
  ) {
    val snapshotBefore = seedGraphs(
      modelCase = modelCase,
      count = 1
    )
    val child = snapshotBefore.parents
      .flatMap(modelCase::relatedDeleteValues)
      .single()
    assertRelatedDelete(
      modelCase = modelCase,
      terminal = terminal,
      expectedCount = 1,
      execute = { modelCase.executeRelatedDelete(value = child) },
      observe = { modelCase.observeRelatedDelete(value = child) }
    )
  }

  private fun <T, R> assertBulkDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal
  ) {
    val snapshotBefore = seedGraphs(
      modelCase = modelCase,
      count = 2
    )
    val children = snapshotBefore.parents
      .flatMap(modelCase::relatedDeleteValues)
    assertRelatedDelete(
      modelCase = modelCase,
      terminal = terminal,
      expectedCount = children.size,
      execute = { modelCase.executeRelatedBulkDelete(values = children) },
      observe = { modelCase.observeRelatedBulkDelete(values = children) }
    )
  }

  private fun <T, R> assertTableDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal
  ) {
    val snapshotBefore = seedGraphs(
      modelCase = modelCase,
      count = 2
    )
    val expectedCount = snapshotBefore.parents
      .flatMap(modelCase::relatedDeleteValues)
      .size
    assertRelatedDelete(
      modelCase = modelCase,
      terminal = terminal,
      expectedCount = expectedCount,
      execute = modelCase::executeRelatedTableDelete,
      observe = modelCase::observeRelatedTableDelete
    )
  }

  private fun <T, R> assertRelatedDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal,
    expectedCount: Int,
    execute: () -> Int,
    observe: () -> Single<Int>
  ) {
    val deletedCount = terminal.select(
      execute = execute,
      observe = observe
    )
    assertThat(deletedCount).isEqualTo(expectedCount)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = emptyList(),
        related = emptyList<Any?>()
      )
    )
  }

  private fun <T, R> seedGraphs(
    modelCase: ReferencedDeleteModelCase<T, R>,
    count: Int
  ): DatabaseSnapshot<T> {
    repeat(count) { index ->
      val value = modelCase.newValue(sequence = index + 1)
      assertSeedInserted(
        result = modelCase
          .insert(value = value)
          .execute(),
        modelName = modelCase.name
      )
    }
    return captureDatabaseSnapshot(modelCase = modelCase)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.referencedDeleteCases
  }
}
