package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.ReferencedDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.ReactiveOperation
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class ReferencedRelationshipDeleteTest(
  private val modelCase: ReferencedDeleteModelCase<*, *>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeDeletesReferencedChild() = assertSingleDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeDeletesReferencedChild() = assertSingleDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkDeletesReferencedChildren() = assertBulkDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkDeletesReferencedChildren() = assertBulkDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeDeletesReferencedChildrenByTable() = assertTableDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeDeletesReferencedChildrenByTable() = assertTableDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T, R> assertSingleDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal
  ) {
    val parents = seedRows(
      modelCase = modelCase,
      count = 1,
      firstSequence = 1
    )
    val child = parents
      .flatMap(modelCase::relatedDeleteValues)
      .single()
    assertRelatedDelete(
      modelCase = modelCase,
      terminal = terminal,
      expectedCount = 1,
      execute = { modelCase.executeRelatedDelete(value = child) },
      observe = {
        modelCase.observeRelatedDelete(
          adapter = RxJava2Adapter,
          value = child
        )
      }
    )
  }

  private fun <T, R> assertBulkDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal
  ) {
    val parents = seedRows(
      modelCase = modelCase,
      count = 2,
      firstSequence = 1
    )
    val children = parents
      .flatMap(modelCase::relatedDeleteValues)
    assertRelatedDelete(
      modelCase = modelCase,
      terminal = terminal,
      expectedCount = children.size,
      execute = { modelCase.executeRelatedBulkDelete(values = children) },
      observe = {
        modelCase.observeRelatedBulkDelete(
          adapter = RxJava2Adapter,
          values = children
        )
      }
    )
  }

  private fun <T, R> assertTableDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal
  ) {
    val parents = seedRows(
      modelCase = modelCase,
      count = 2,
      firstSequence = 1
    )
    val expectedCount = parents
      .flatMap(modelCase::relatedDeleteValues)
      .size
    assertRelatedDelete(
      modelCase = modelCase,
      terminal = terminal,
      expectedCount = expectedCount,
      execute = modelCase::executeRelatedTableDelete,
      observe = { modelCase.observeRelatedTableDelete(RxJava2Adapter) }
    )
  }

  private fun <T, R> assertRelatedDelete(
    modelCase: ReferencedDeleteModelCase<T, R>,
    terminal: OperationTerminal,
    expectedCount: Int,
    execute: () -> Int,
    observe: () -> ReactiveOperation<Int>
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

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.referencedDeleteCases
  }
}
