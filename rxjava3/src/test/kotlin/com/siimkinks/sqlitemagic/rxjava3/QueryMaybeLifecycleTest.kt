package com.siimkinks.sqlitemagic.rxjava3

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.newConnection
import com.siimkinks.sqlitemagic.withWorkers
import io.reactivex.rxjava3.core.MaybeObserver
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class QueryMaybeLifecycleTest {
  @Test
  fun `cancellation in onSubscribe prevents running the query`() {
    val observer = object : TestObserver<String>() {
      override fun onSubscribe(disposable: Disposable) {
        super.onSubscribe(disposable)
        dispose()
      }
    }
    val query = object : Query<String>(mock()) {
      override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): String =
        error("Cancelled query must not run")
    }
    query
      .run()
      .subscribe(observer)
    observer.assertEmpty()
  }

  @Test
  fun `execution inside a transaction is rejected before running the query`() {
    val connection = newConnection().dbConnection
    val query = object : Query<String>(connection) {
      override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): String =
        error("Rejected query must not run")
    }
    val transaction = connection.newTransaction()
    val (blocking, observed) = try {
      Pair(
        first = assertThrows(IllegalStateException::class.java, query::runBlocking),
        second = query
          .run()
          .test()
      )
    } finally {
      transaction.end()
    }

    val message = "Cannot execute observable query in a transaction."
    assertThat(blocking.message).isEqualTo(message)
    observed
      .assertNoValues()
      .assertError { it.message == message }
  }

  @Test
  fun `run passes disposal to the query and drops the result of a disposed subscription`() {
    val observer = TestObserver<List<Boolean>>()
    val seenDisposal = mutableListOf<Boolean>()
    val query = object : Query<List<Boolean>>(mock()) {
      override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): List<Boolean> {
        seenDisposal += isDisposed()
        observer.dispose()
        seenDisposal += isDisposed()
        return seenDisposal.toList()
      }
    }

    query
      .run()
      .subscribe(observer)

    observer.assertEmpty()
    assertThat(seenDisposal)
      .containsExactly(false, true)
      .inOrder()
  }

  @Test
  fun `simultaneous cancellation and delivery deliver the result at most once`() = withWorkers { workers ->
    repeat(100) {
      val barrier = CyclicBarrier(2)
      val observer = RecordingObserver()
      val query = object : Query<String>(mock()) {
        override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): String {
          barrier.await(5, SECONDS)
          return "result"
        }
      }
      val execution = workers.submit {
        query
          .run()
          .subscribe(observer)
      }
      val cancellation = workers.submit {
        barrier.await(5, SECONDS)
        observer.dispose()
      }
      execution.get(5, SECONDS)
      cancellation.get(5, SECONDS)
      assertThat(observer.outcome).isAnyOf(Outcome(values = listOf("result")), Outcome())
    }
  }

  @Test
  fun `decoded success empty and mapping error preserve terminal behavior`() {
    val failure = IllegalArgumentException("Mapping failed")
    MappingResult.entries.forEach { result ->
      val query = object : Query<List<String>?>(mock()) {
        override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): List<String>? = when (result) {
          MappingResult.SUCCESS -> listOf("decoded")
          MappingResult.EMPTY -> null
          MappingResult.ERROR -> throw failure
        }
      }
      val observer = query
        .run()
        .test()
        .withTag(result.name)
      when (result) {
        MappingResult.SUCCESS -> observer.assertResult(listOf("decoded"))
        MappingResult.EMPTY -> observer.assertResult()
        MappingResult.ERROR -> observer
          .assertNoValues()
          .assertError(failure)
          .assertNotComplete()
      }
    }
  }

  @Test
  fun `cancelled mapping error is reported to plugins`() = withWorkers { workers ->
    val failure = IllegalArgumentException("Mapping failed after cancellation")
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val errors = CopyOnWriteArrayList<Throwable>()
    val observer = RecordingObserver()
    val query = object : Query<String>(mock()) {
      override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): String {
        entered.countDown()
        await(release)
        throw failure
      }
    }
    RxJavaPlugins.setErrorHandler(errors::add)
    try {
      val execution = workers.submit {
        query
          .run()
          .subscribe(observer)
      }
      await(entered)
      observer.dispose()
      release.countDown()
      execution.get(5, SECONDS)
      assertThat(errors).containsExactly(failure)
      assertThat(observer.outcome).isEqualTo(Outcome())
    } finally {
      release.countDown()
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `fatal mapping failures escape run`() {
    val failure = LinkageError("fatal mapper failure")
    val query = object : Query<String>(mock()) {
      override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): String = throw failure
    }

    val thrown = assertThrows(LinkageError::class.java) {
      query
        .run()
        .test()
    }

    assertThat(thrown).isSameInstanceAs(failure)
  }

  @Test
  fun `run preserves Maybe assembly hook`() {
    val assemblies = AtomicInteger()
    val query = object : Query<String>(mock()) {
      override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean) = "result"
    }
    RxJavaPlugins.setOnMaybeAssembly {
      assemblies.incrementAndGet()
      it
    }
    try {
      val result = query.run()
      assertThat(assemblies.get()).isEqualTo(1)
      result
        .test()
        .assertResult("result")
    } finally {
      RxJavaPlugins.reset()
    }
  }

  private class RecordingObserver : MaybeObserver<String> {
    private val values = CopyOnWriteArrayList<String>()
    private val errors = CopyOnWriteArrayList<Throwable>()
    private val completions = AtomicInteger()
    private val subscription = AtomicReference<Disposable>()
    val outcome
      get() = Outcome(
        values = values.toList(),
        errors = errors.toList(),
        completions = completions.get()
      )

    override fun onSubscribe(disposable: Disposable) = subscription.set(disposable)

    override fun onSuccess(result: String) {
      values.add(result)
    }

    override fun onError(failure: Throwable) {
      errors.add(failure)
    }

    override fun onComplete() {
      completions.incrementAndGet()
    }

    fun dispose() = checkNotNull(subscription.get()).dispose()
  }

  private data class Outcome(
    val values: List<String> = emptyList(),
    val errors: List<Throwable> = emptyList(),
    val completions: Int = 0
  )

  private enum class MappingResult {
    SUCCESS,
    EMPTY,
    ERROR
  }

  private fun await(latch: CountDownLatch) =
    check(latch.await(5, SECONDS)) { "Timed out waiting for query lifecycle boundary" }
}
