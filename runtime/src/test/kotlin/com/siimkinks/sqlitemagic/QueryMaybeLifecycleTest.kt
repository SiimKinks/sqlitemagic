package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import io.reactivex.MaybeObserver
import io.reactivex.disposables.Disposable
import io.reactivex.observers.TestObserver
import io.reactivex.plugins.RxJavaPlugins
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class QueryMaybeLifecycleTest {
  @Test
  fun `cancellation in onSubscribe prevents acquisition and mapping`() {
    val observer = object : TestObserver<Cursor>() {
      override fun onSubscribe(disposable: Disposable) {
        super.onSubscribe(disposable)
        dispose()
      }
    }
    val query = object : Query<Cursor>(mock()) {
      override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl): Cursor =
        error("Cancelled query must not acquire a cursor")

      override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): Cursor =
        error("Cancelled query must not map a cursor")
    }
    query
      .run()
      .subscribe(observer)
    observer.assertEmpty()
  }

  @Test
  fun `cancellation before mapping returns discards exactly once`() = withWorkers { workers ->
    val cursor = mock<Cursor>()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val observer = RecordingObserver()
    val query = cursorQuery(
      cursor = cursor,
      duringMap = {
        entered.countDown()
        await(release)
      }
    )
    val execution = workers.submit {
      query
        .run()
        .subscribe(observer)
    }
    try {
      await(entered)
      observer.dispose()
      verify(cursor, never()).close()
      release.countDown()
      execution.get(5, TimeUnit.SECONDS)
      assertThat(observer.values).isEmpty()
      assertThat(observer.errors).isEmpty()
      assertThat(observer.completions.get()).isEqualTo(0)
      verify(cursor).close()
    } finally {
      release.countDown()
    }
  }

  @Test
  fun `cancellation during winning onSuccess leaves cursor owned by subscriber`() = withWorkers { workers ->
    val cursor = mock<Cursor>()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val observer = RecordingObserver {
      entered.countDown()
      await(release)
    }
    val execution = workers.submit {
      cursorQuery(cursor)
        .run()
        .subscribe(observer)
    }
    try {
      await(entered)
      observer.dispose()
      verify(cursor, never()).close()
      release.countDown()
      execution.get(5, TimeUnit.SECONDS)
      assertThat(observer.values).containsExactly(cursor)
      assertThat(observer.errors).isEmpty()
      assertThat(observer.completions.get()).isEqualTo(0)
      verify(cursor, never()).close()
      cursor.close()
      verify(cursor).close()
    } finally {
      release.countDown()
    }
  }

  @Test
  fun `simultaneous cancellation and delivery give each cursor exactly one owner`() = withWorkers { workers ->
    repeat(100) {
      val cursor = mock<Cursor>()
      val barrier = CyclicBarrier(2)
      val discarded = AtomicInteger()
      val observer = RecordingObserver()
      val query = object : Query<Cursor>(mock()) {
        override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl) = cursor

        override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): Cursor {
          barrier.await(5, TimeUnit.SECONDS)
          return checkNotNull(cursor)
        }

        override fun discardResult(result: Cursor) {
          discarded.incrementAndGet()
          result.close()
        }
      }
      val execution = workers.submit {
        query
          .run()
          .subscribe(observer)
      }
      val cancellation = workers.submit {
        barrier.await(5, TimeUnit.SECONDS)
        observer.dispose()
      }
      execution.get(5, TimeUnit.SECONDS)
      cancellation.get(5, TimeUnit.SECONDS)
      assertThat(observer.values.size + discarded.get()).isEqualTo(1)
      assertThat(observer.errors).isEmpty()
      assertThat(observer.completions.get()).isEqualTo(0)
      if (observer.values.isNotEmpty()) {
        assertThat(observer.values).containsExactly(cursor)
        verify(cursor, never()).close()
        cursor.close()
      }
      verify(cursor).close()
    }
  }

  @Test
  fun `decoded success empty and mapping error preserve terminal behavior`() {
    val failure = IllegalArgumentException("Mapping failed")
    for (outcome in listOf("success", "empty", "error")) {
      val cursor = mock<Cursor>()
      val query = object : Query<List<String>>(mock()) {
        override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl) = cursor

        override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): List<String>? =
          checkNotNull(cursor).use {
            when (outcome) {
              "success" -> listOf("decoded")
              "empty" -> null
              else -> throw failure
            }
          }
      }
      val observer = query
        .run()
        .test()
      when (outcome) {
        "success" -> observer.assertResult(listOf("decoded"))
        "empty" -> observer.assertResult()
        else -> observer.assertFailure(failure::equals)
      }
      verify(cursor).close()
    }
  }

  @Test
  fun `cancelled mapping error is reported to plugins after mapper closes cursor`() = withWorkers { workers ->
    val cursor = mock<Cursor>()
    val failure = IllegalArgumentException("Mapping failed after cancellation")
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val errors = CopyOnWriteArrayList<Throwable>()
    val observer = RecordingObserver()
    val query = object : Query<Cursor>(mock()) {
      override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl) = cursor

      override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): Cursor = checkNotNull(cursor).use {
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
      verify(cursor, never()).close()
      release.countDown()
      execution.get(5, TimeUnit.SECONDS)
      assertThat(errors).containsExactly(failure)
      assertThat(observer.values).isEmpty()
      assertThat(observer.errors).isEmpty()
      assertThat(observer.completions.get()).isEqualTo(0)
      verify(cursor).close()
    } finally {
      release.countDown()
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `run preserves Maybe assembly hook`() {
    val assemblies = AtomicInteger()
    val cursor = mock<Cursor>()
    RxJavaPlugins.setOnMaybeAssembly {
      assemblies.incrementAndGet()
      it
    }
    try {
      val result = cursorQuery(cursor).run()
      assertThat(assemblies.get()).isEqualTo(1)
      result
        .test()
        .assertResult(cursor)
      verify(cursor, never()).close()
      cursor.close()
      verify(cursor).close()
    } finally {
      RxJavaPlugins.reset()
    }
  }

  private fun cursorQuery(
    cursor: Cursor,
    duringMap: () -> Unit = {}
  ) = object : Query<Cursor>(mock()) {
    override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl) = cursor

    override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): Cursor {
      duringMap()
      return checkNotNull(cursor)
    }

    override fun discardResult(result: Cursor) = result.close()
  }

  private class RecordingObserver(
    private val duringSuccess: () -> Unit = {}
  ) : MaybeObserver<Cursor> {
    val values = CopyOnWriteArrayList<Cursor>()
    val errors = CopyOnWriteArrayList<Throwable>()
    val completions = AtomicInteger()
    private val subscription = AtomicReference<Disposable>()

    override fun onSubscribe(disposable: Disposable) = subscription.set(disposable)

    override fun onSuccess(result: Cursor) {
      values.add(result)
      duringSuccess()
    }

    override fun onError(failure: Throwable) {
      errors.add(failure)
    }

    override fun onComplete() {
      completions.incrementAndGet()
    }

    fun dispose() = checkNotNull(subscription.get()).dispose()
  }

  private fun await(latch: CountDownLatch) {
    check(latch.await(5, TimeUnit.SECONDS)) { "Timed out waiting for query lifecycle boundary" }
  }

  private fun withWorkers(block: (ExecutorService) -> Unit) {
    val workers = Executors.newFixedThreadPool(2)
    try {
      block(workers)
    } finally {
      workers.shutdownNow()
      check(workers.awaitTermination(5, TimeUnit.SECONDS)) { "Query lifecycle workers did not stop" }
    }
  }
}
