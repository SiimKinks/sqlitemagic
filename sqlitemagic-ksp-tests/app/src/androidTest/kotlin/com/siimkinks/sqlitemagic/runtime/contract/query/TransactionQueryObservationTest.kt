package com.siimkinks.sqlitemagic.runtime.contract.query

import android.R
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransactionObservationCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class TransactionQueryObservationTest : RuntimeDatabaseTest() {
  @Test
  fun nestedSameTableCommitEmitsOnlyFinalResult() {
    val stringRows = listOf(
      TransactionObservationCatalog.string.insert(sequence = 1),
      TransactionObservationCatalog.string.insert(sequence = 2)
    )
    val observer = observeMatchingValues()
      .test()
      .assertValuesOnly(emptyList())
    try {
      val simpleRows = mutableListOf<SimpleMutableEntity>()
      inTransaction {
        simpleRows += TransactionObservationCatalog.simple.insert(sequence = 1)
        assertRows(
          expectedSimpleRows = simpleRows,
          expectedStringRows = stringRows
        )
        assertThat(TransactionObservationCatalog.matchingValues())
          .isEqualTo(simpleRows.map(SimpleMutableEntity::value))
        observer.assertValuesOnly(emptyList())

        inTransaction {
          simpleRows += TransactionObservationCatalog.simple.insert(sequence = 2)
          assertRows(
            expectedSimpleRows = simpleRows,
            expectedStringRows = stringRows
          )
          assertThat(TransactionObservationCatalog.matchingValues())
            .isEqualTo(simpleRows.map(SimpleMutableEntity::value))
          observer.assertValuesOnly(emptyList())
        }

        assertRows(
          expectedSimpleRows = simpleRows,
          expectedStringRows = stringRows
        )
        observer.assertValuesOnly(emptyList())
      }

      observer.assertValuesOnly(
        emptyList(),
        simpleRows.map(SimpleMutableEntity::value)
      )
      assertRows(
        expectedSimpleRows = simpleRows,
        expectedStringRows = stringRows
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun nestedMultiTableCommitEmitsOnlyFinalResult() {
    val initialSimple = TransactionObservationCatalog.simple.insert(sequence = 1)
    val initialString = TransactionObservationCatalog.string.insert(sequence = 1)
    val observer = observeMatchingValues()
      .test()
      .assertValuesOnly(listOf(initialSimple.value))
    try {
      val simpleRows = mutableListOf(initialSimple)
      val stringRows = mutableListOf(initialString)
      inTransaction {
        val secondSimple = TransactionObservationCatalog.simple.insert(sequence = 2)
        simpleRows += secondSimple
        assertThat(TransactionObservationCatalog.matchingValues())
          .isEqualTo(listOf(initialSimple.value))
        observer.assertValuesOnly(listOf(initialSimple.value))

        inTransaction {
          val secondString = TransactionObservationCatalog.string.insert(sequence = 2)
          stringRows += secondString
          assertThat(TransactionObservationCatalog.matchingValues())
            .isEqualTo(simpleRows.map(SimpleMutableEntity::value))
          observer.assertValuesOnly(listOf(initialSimple.value))
        }

        assertRows(
          expectedSimpleRows = simpleRows,
          expectedStringRows = stringRows
        )
        observer.assertValuesOnly(listOf(initialSimple.value))
      }

      observer.assertValuesOnly(
        listOf(initialSimple.value),
        simpleRows.map(SimpleMutableEntity::value)
      )
      assertRows(
        expectedSimpleRows = simpleRows,
        expectedStringRows = stringRows
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun multiTableRollbackEmitsNothingAndRestoresExactTables() {
    val initialSimple = TransactionObservationCatalog.simple.insert(sequence = 1)
    val initialString = TransactionObservationCatalog.string.insert(sequence = 1)
    val observer = observeMatchingValues()
      .test()
      .assertValuesOnly(listOf(initialSimple.value))
    try {
      val transaction = SqliteMagic.newTransaction()
      try {
        val temporarySimple = TransactionObservationCatalog.simple.insert(sequence = 2)
        inTransaction {
          val temporaryString = TransactionObservationCatalog.string.insert(sequence = 2)
          assertRows(
            expectedSimpleRows = listOf(initialSimple, temporarySimple),
            expectedStringRows = listOf(initialString, temporaryString)
          )
          assertThat(TransactionObservationCatalog.matchingValues())
            .isEqualTo(listOf(initialSimple.value, temporarySimple.value))
          observer.assertValuesOnly(listOf(initialSimple.value))
        }
        assertThat(TransactionObservationCatalog.matchingValues())
          .isEqualTo(listOf(initialSimple.value, temporarySimple.value))
        observer.assertValuesOnly(listOf(initialSimple.value))
      } finally {
        transaction.end()
      }

      observer.assertValuesOnly(listOf(initialSimple.value))
      assertRows(
        expectedSimpleRows = listOf(initialSimple),
        expectedStringRows = listOf(initialString)
      )
      assertThat(TransactionObservationCatalog.matchingValues()).isEqualTo(listOf(initialSimple.value))
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun nestedInnerRollbackDefeatsOuterSuccessfulCommit() {
    val initialSimple = TransactionObservationCatalog.simple.insert(sequence = 1)
    val initialString = TransactionObservationCatalog.string.insert(sequence = 1)
    val observer = observeMatchingValues()
      .test()
      .assertValuesOnly(listOf(initialSimple.value))
    try {
      inTransaction {
        val temporarySimple = TransactionObservationCatalog.simple.insert(sequence = 2)
        val nestedTransaction = SqliteMagic.newTransaction()
        try {
          val temporaryString = TransactionObservationCatalog.string.insert(sequence = 2)
          assertRows(
            expectedSimpleRows = listOf(initialSimple, temporarySimple),
            expectedStringRows = listOf(initialString, temporaryString)
          )
          assertThat(TransactionObservationCatalog.matchingValues())
            .isEqualTo(listOf(initialSimple.value, temporarySimple.value))
        } finally {
          nestedTransaction.end()
        }
      }

      observer.assertValuesOnly(listOf(initialSimple.value))
      assertRows(
        expectedSimpleRows = listOf(initialSimple),
        expectedStringRows = listOf(initialString)
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun emptySuccessfulTransactionEmitsNothing() {
    val initialSimple = TransactionObservationCatalog.simple.insert(sequence = 1)
    val initialString = TransactionObservationCatalog.string.insert(sequence = 1)
    val observer = observeMatchingValues()
      .test()
      .assertValuesOnly(listOf(initialSimple.value))
    try {
      inTransaction {
        assertThat(TransactionObservationCatalog.matchingValues()).isEqualTo(listOf(initialSimple.value))
      }

      observer.assertValuesOnly(listOf(initialSimple.value))
      assertRows(
        expectedSimpleRows = listOf(initialSimple),
        expectedStringRows = listOf(initialString)
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun transactionCreatedFromCommitNotificationWritesAndNotifiesOnceMore() {
    val callbackCount = AtomicInteger()
    val simpleRows = mutableListOf<SimpleMutableEntity>()
    val stringRows = mutableListOf<StringIdEntity>()
    val observer = observeMatchingValues()
      .doAfterNext {
        if (callbackCount.incrementAndGet() == 2) {
          inTransaction {
            simpleRows += TransactionObservationCatalog.simple.insert(sequence = 2)
            stringRows += TransactionObservationCatalog.string.insert(sequence = 2)
          }
        }
      }
      .test()
      .assertValuesOnly(emptyList())
    try {
      inTransaction {
        simpleRows += TransactionObservationCatalog.simple.insert(sequence = 1)
        stringRows += TransactionObservationCatalog.string.insert(sequence = 1)
      }

      observer.assertValuesOnly(
        emptyList(),
        listOf(simpleRows[0].value),
        simpleRows.map(SimpleMutableEntity::value)
      )
      assertThat(callbackCount.get()).isEqualTo(3)
      assertRows(
        expectedSimpleRows = simpleRows,
        expectedStringRows = stringRows
      )
    } finally {
      observer.dispose()
    }
  }

  private fun observeMatchingValues() = TransactionObservationCatalog.observeMatchingValues()

  private fun assertRows(
    expectedSimpleRows: List<SimpleMutableEntity>,
    expectedStringRows: List<StringIdEntity>
  ) {
    assertRowsIgnoringOrder(
      table = TransactionObservationCatalog.simple.table,
      expected = expectedSimpleRows
    )
    assertRowsIgnoringOrder(
      table = TransactionObservationCatalog.string.table,
      expected = expectedStringRows
    )
  }
}
