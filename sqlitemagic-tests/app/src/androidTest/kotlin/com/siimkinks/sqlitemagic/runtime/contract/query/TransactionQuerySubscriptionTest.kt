package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransactionObservationCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.Recording
import com.siimkinks.sqlitemagic.runtime.support.reactiveAdapters
import com.siimkinks.sqlitemagic.runtime.support.record
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

@RunWith(Parameterized::class)
internal class TransactionQuerySubscriptionTest(
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun sameThreadSubscriptionFailsWithExpectedMessage() {
    val transaction = SqliteMagic.newTransaction()
    try {
      TransactionObservationCatalog
        .observeMatchingValues(adapter)
        .record()
        .assertFailure(::isTransactionSubscriptionError)
        .dispose()
    } finally {
      transaction.end()
    }
  }

  @Test
  fun sameThreadBackpressureSubscriptionFailsAfterZeroInitialRequest() {
    val transaction = SqliteMagic.newTransaction()
    try {
      TransactionObservationCatalog
        .observeMatchingValues(adapter)
        .record(initialDemand = 0)
        .assertFailure(::isTransactionSubscriptionError)
        .apply {
          request(1L)
          assertFailure(::isTransactionSubscriptionError)
          dispose()
        }
    } finally {
      transaction.end()
    }
  }

  @Test
  fun differentThreadSubscriptionReadsCommittedStateAfterOwnerTransactionEnds() {
    val initialSimple = TransactionObservationCatalog.simple.insert(sequence = 1)
    TransactionObservationCatalog.string.insert(sequence = 1)
    val transaction = SqliteMagic.newTransaction()
    val executor = Executors.newSingleThreadExecutor()
    val rawQueryEmitted = CountDownLatch(1)
    val queryFinished = CountDownLatch(1)
    val workerError = AtomicReference<Throwable?>()
    val observer = Recording<List<String?>>()
    var ownerTransactionOpen = true
    try {
      val transactionSimple = TransactionObservationCatalog.simple.insert(sequence = 2)
      TransactionObservationCatalog.string.insert(sequence = 2)
      assertThat(TransactionObservationCatalog.matchingValues())
        .isEqualTo(listOf(initialSimple.value, transactionSimple.value))

      executor.execute {
        try {
          TransactionObservationCatalog
            .observeMatchingQueries(adapter)
            .onEach { rawQueryEmitted.countDown() }
            .map { checkNotNull(it.runBlocking()) }
            .subscribe(observer)
        } catch (error: Throwable) {
          workerError.set(error)
        } finally {
          queryFinished.countDown()
        }
      }

      assertThat(rawQueryEmitted.await(TIMEOUT_SECONDS, SECONDS)).isTrue()
      transaction.markSuccessful()
      transaction.end()
      ownerTransactionOpen = false

      assertThat(queryFinished.await(TIMEOUT_SECONDS, SECONDS)).isTrue()
      assertThat(workerError.get()).isNull()
      observer.assertValuesOnly(
        listOf(initialSimple.value, transactionSimple.value),
        listOf(initialSimple.value, transactionSimple.value)
      )

      val nextSimple = TransactionObservationCatalog.simple.newValue(sequence = 3)
      inTransaction {
        TransactionObservationCatalog.simple.insert(value = nextSimple)
        TransactionObservationCatalog.string.insert(sequence = 3)
      }
      observer.assertValuesOnly(
        listOf(initialSimple.value, transactionSimple.value),
        listOf(initialSimple.value, transactionSimple.value),
        listOf(initialSimple.value, transactionSimple.value, nextSimple.value)
      )
    } finally {
      if (ownerTransactionOpen) {
        transaction.end()
      }
      observer.dispose()
      executor.shutdownNow()
      assertThat(executor.awaitTermination(TIMEOUT_SECONDS, SECONDS)).isTrue()
    }
  }

  private fun isTransactionSubscriptionError(error: Throwable) =
    error is IllegalStateException && error.message == TRANSACTION_SUBSCRIPTION_ERROR

  companion object {
    private const val TRANSACTION_SUBSCRIPTION_ERROR = "Cannot subscribe to observable query in a transaction."
    private const val TIMEOUT_SECONDS = 5L

    @JvmStatic
    @Parameters(name = "{0}")
    fun adapters() = reactiveAdapters
  }
}
