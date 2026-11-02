package com.siimkinks.sqlitemagic.runtime.contract.transaction

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQueryOnce
import com.siimkinks.sqlitemagic.coroutines.withTransaction
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.CoroutinesAdapter
import com.siimkinks.sqlitemagic.runtime.support.Recording
import com.siimkinks.sqlitemagic.runtime.support.reactiveAdapters
import com.siimkinks.sqlitemagic.runtime.support.record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.State.TIMED_WAITING
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

internal class CoroutineTransactionTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  private val workers = Executors.newSingleThreadExecutor()

  @After
  fun stopWorkers() {
    workers.shutdownNow()
  }

  @Test
  fun writesOnSeveralThreadsCommitTogetherAndObserversOfEveryAdapterSeeThemOnce() {
    data class Outcome(
      val readInside: List<String?>,
      val readOnAnotherThread: List<String?>,
      val observedDuring: Map<String, List<List<String?>>>,
      val committed: List<String?>,
      val ranOnSeveralThreads: Boolean
    )

    val observers = reactiveAdapters.associate { adapter ->
      adapter.toString() to adapter
        .runQuery(valuesQuery())
        .record()
    }
    try {
      val threads = ConcurrentHashMap.newKeySet<Thread>()
      var readInside = emptyList<String?>()
      var readOnAnotherThread = emptyList<String?>()
      var observedDuring = emptyMap<String, List<List<String?>>>()
      runBlocking {
        withTransaction {
          threads += Thread.currentThread()
          entity("first")
            .insert()
            .await()
          withContext(Dispatchers.Default) {
            threads += Thread.currentThread()
            entity("second")
              .insert()
              .execute()
          }
          inTransaction {
            threads += Thread.currentThread()
            entity("third")
              .insert()
              .execute()
          }
          readInside = valuesQuery()
            .observe()
            .runQueryOnce()
          readOnAnotherThread = workers
            .submit(::committedValues)
            .get(TIMEOUT_SECONDS, SECONDS)
          observedDuring = observers.mapValues { (_, observer) -> observer.values.toList() }
        }
      }
      observers.values.forEach { it.awaitValueCount(2) }

      assertThat(
        Outcome(
          readInside = readInside,
          readOnAnotherThread = readOnAnotherThread,
          observedDuring = observedDuring,
          committed = committedValues(),
          ranOnSeveralThreads = threads.size > 1
        )
      ).isEqualTo(
        Outcome(
          readInside = listOf("first", "second", "third"),
          readOnAnotherThread = emptyList(),
          observedDuring = observers.mapValues { listOf(emptyList()) },
          committed = listOf("first", "second", "third"),
          ranOnSeveralThreads = true
        )
      )
      observers.forEach { (adapter, observer) ->
        observer
          .withTag(adapter)
          .assertValuesOnly(emptyList(), listOf("first", "second", "third"))
      }
    } finally {
      observers.values.forEach(Recording<*>::dispose)
    }
  }

  @Test
  fun failureOfTheBlockOrAChildRollsBackWithoutNotifyingObservers() {
    data class Outcome(
      val failure: String?,
      val committed: List<String?>,
      val observed: List<List<String?>>,
      val committedAfterNextWrite: List<String?>
    )

    val cases = mapOf<String, suspend () -> Unit>(
      "the block throws" to {
        withTransaction {
          entity("rolled back")
            .insert()
            .await()
          error("block")
        }
      },
      "a child coroutine fails" to {
        withTransaction {
          launch(Dispatchers.Default) {
            entity("rolled back")
              .insert()
              .await()
            error("child")
          }
        }
      }
    )

    val outcomes = cases.mapValues { (_, run) ->
      SqliteMagic
        .getDefaultConnection()
        .clearData()
      val observer = CoroutinesAdapter
        .runQuery(valuesQuery())
        .record()
      try {
        val failure = runCatching { runBlocking { run() } }.exceptionOrNull()
        val committed = committedValues()
        val observed = observer.values.toList()
        entity("next")
          .insert()
          .execute()
        Outcome(
          failure = failure?.message,
          committed = committed,
          observed = observed,
          committedAfterNextWrite = committedValues()
        )
      } finally {
        observer.dispose()
      }
    }

    val rolledBack = { failure: String ->
      Outcome(
        failure = failure,
        committed = emptyList(),
        observed = listOf(emptyList()),
        committedAfterNextWrite = listOf("next")
      )
    }
    assertThat(outcomes).isEqualTo(
      mapOf(
        "the block throws" to rolledBack("block"),
        "a child coroutine fails" to rolledBack("child")
      )
    )
  }

  @Test
  fun collectingAnObservationInsideTheTransactionIsRejected() {
    val failure = runCatching {
      runBlocking {
        withTransaction {
          valuesQuery()
            .observe()
            .first()
        }
      }
    }.exceptionOrNull()

    assertThat(listOf(failure is IllegalStateException, failure?.message))
      .containsExactly(true, "Cannot subscribe to observable query in a transaction.")
      .inOrder()
  }

  @Test
  fun writerOnAnotherThreadWaitsForTheTransactionUntilItYields() {
    val writerThread = AtomicReference<Thread>()
    val yielded = runBlocking {
      withTransaction {
        entity("first")
          .insert()
          .await()
        val concurrent = workers.submit {
          writerThread.set(Thread.currentThread())
          entity("concurrent")
            .insert()
            .execute()
        }
        while (!concurrent.isDone && writerThread.get()?.state != TIMED_WAITING) {
          Thread.yield()
        }
        yieldIfContended().also {
          concurrent.get(TIMEOUT_SECONDS, SECONDS)
          entity("second")
            .insert()
            .await()
        }
      }
    }

    assertThat(listOf(yielded, committedValues()))
      .containsExactly(true, listOf("first", "concurrent", "second"))
      .inOrder()
  }

  private fun entity(value: String) = SimpleMutableEntity(value = value)

  private fun valuesQuery() = SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
    .FROM(SIMPLE_MUTABLE_ENTITY)
    .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())

  private fun committedValues() = valuesQuery().execute()

  private fun Recording<*>.awaitValueCount(count: Int) {
    val deadline = System.nanoTime() + SECONDS.toNanos(TIMEOUT_SECONDS)
    while (values.size < count && System.nanoTime() < deadline) {
      Thread.yield()
    }
  }

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}
