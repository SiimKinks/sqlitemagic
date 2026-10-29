package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedIdCallbackEntityTable.Companion.GENERATED_ID_CALLBACK_ENTITY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.fixture.model.GeneratedIdCallbackEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class GeneratedIdCallbackFailureRuntimeTest(
  private val operation: CallbackOperation,
  private val transactionMode: MutationTransactionMode
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun retainedGeneratedWriteRefreshesOnlyAfterCommit() {
    val query = SELECT
      .FROM(GENERATED_ID_CALLBACK_ENTITY)
      .ORDER_BY(GENERATED_ID_CALLBACK_ENTITY.ID.ASC())
      .compile()
    val observer = query
      .observe()
      .runQuery()
      .test()
    val entity = GeneratedIdCallbackEntity().apply {
      value = "inserted"
      rejectGeneratedId = true
    }
    val transaction = when (transactionMode) {
      MutationTransactionMode.NONE -> null
      else -> SqliteMagic.newTransaction()
    }
    try {
      val failure = assertThrows(IllegalStateException::class.java) {
        when (operation) {
          CallbackOperation.INSERT -> entity.insert()
            .execute()
          CallbackOperation.PERSIST -> entity.persist()
            .execute()
        }
      }
      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("Generated ID callback failed")
      assertThat(entity.id)
        .isNull()
      if (transaction != null) observer.assertValueCount(1)
      if (transactionMode == MutationTransactionMode.COMMIT) transaction?.markSuccessful()
    } finally {
      transaction?.end()
    }
    try {
      when (transactionMode) {
        MutationTransactionMode.ROLLBACK -> {
          assertThat(query.execute())
            .isEmpty()
          observer.assertValuesOnly(emptyList())
        }
        else -> {
          val rows = query.execute()
          assertThat(rows)
            .hasSize(1)
          val rowId = rows.single().id
          assertThat(rowId)
            .isNotNull()
          val expected = listOf(GeneratedIdCallbackEntity().apply {
            id = rowId
            value = "inserted"
          })
          assertThat(rows)
            .isEqualTo(expected)
          observer.assertValuesOnly(emptyList(), expected)
        }
      }
    } finally {
      observer.dispose()
    }
  }

  enum class CallbackOperation(private val label: String) {
    INSERT("insert callback"),
    PERSIST("persist callback");

    override fun toString() = label
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0} transaction={1}")
    fun cases() = CallbackOperation.entries.flatMap { operation ->
      MutationTransactionMode.entries.map { arrayOf(operation, it) }
    }
  }
}
