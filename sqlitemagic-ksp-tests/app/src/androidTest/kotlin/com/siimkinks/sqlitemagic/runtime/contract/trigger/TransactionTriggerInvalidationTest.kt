package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TransactionTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun committedTransactionWithTwoGeneratedInsertsInvalidatesOnce() {
    assertCommittedTransaction(
      modelCase = modelCase
    )
  }

  @Test
  fun rolledBackTransactionWithGeneratedInsertsDoesNotInvalidate() {
    assertRolledBackTransaction(
      modelCase = modelCase
    )
  }

  private fun <T> assertCommittedTransaction(
    modelCase: TriggerModelCase<T>
  ) {
    val values = List(
      size = 2,
      init = modelCase::newValue
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        buildList {
          inTransaction {
            addAll(values.map { value ->
              modelCase
                .insert(value = value)
                .execute()
            })
          }
        }
      },
      expected = { results ->
        val insertedResults = results.map { result ->
          insertedResult(
            result = result,
            modelName = modelCase.name
          )
        }
        DatabaseSnapshot(
          parents = values.zip(insertedResults).map { (value, result) ->
            modelCase.expectedAfterInsert(
              value = value,
              result = result
            )
          }
        )
      }
    )
  }

  private fun <T> assertRolledBackTransaction(
    modelCase: TriggerModelCase<T>
  ) {
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertNoQueryInvalidation(
      modelCase = modelCase,
      expected = before,
      operation = {
        val transaction = SqliteMagic.newTransaction()
        try {
          modelCase
            .insert(value = modelCase.newValue(sequence = 1))
            .execute()
          modelCase
            .insert(value = modelCase.newValue(sequence = 2))
            .execute()
        } finally {
          transaction.end()
        }
      }
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = before
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.triggerCases
  }
}
