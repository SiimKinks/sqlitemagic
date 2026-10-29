package com.siimkinks.sqlitemagic.rxjava3

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.RecordingOpenHelper
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.TestGeneratedDatabase
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.closedConnection
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.testDbConnection
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

internal class QueryNotificationsTest {
  @Test
  fun `changes during an emission collapse into one further emission that starts after the last change`() {
    withSingleThreadScheduler { connection, scheduled ->
      val version = AtomicInteger()
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      val seenVersions = mutableListOf<Int>()
      val emitted = CountDownLatch(2)
      val subscription = authorNames(connection).subscribe {
        seenVersions += version.get()
        if (seenVersions.size == 1) {
          entered.countDown()
          release.await()
        }
        emitted.countDown()
      }
      check(entered.await(5, SECONDS))

      repeat(100) {
        version.incrementAndGet()
        connection.sendTableTrigger("author")
      }
      release.countDown()
      check(emitted.await(5, SECONDS))
      scheduled.submit {}.get(5, SECONDS)
      subscription.dispose()

      assertThat(seenVersions).containsExactly(0, 100).inOrder()
    }
  }

  @Test
  fun `changes made while an emission waits for the scheduler join that emission`() {
    withSingleThreadScheduler { connection, scheduled ->
      val release = CountDownLatch(1)
      scheduled.execute { release.await() }
      val version = AtomicInteger()
      val seenVersions = mutableListOf<Int>()
      val subscription = authorNames(connection).subscribe {
        seenVersions += version.get()
      }

      repeat(3) {
        version.incrementAndGet()
        connection.sendTableTrigger("author")
      }
      release.countDown()
      scheduled.submit {}.get(5, SECONDS)
      subscription.dispose()

      assertThat(seenVersions).containsExactly(3)
    }
  }

  @Test
  fun `a write made during an emission emits again after that emission returns`() {
    val connection = testDbConnection(
      database = mock<SupportSQLiteDatabase>(),
      adapterSettings = querySchedulerSettings()
    )
    val events = mutableListOf<String>()
    var emissions = 0

    authorNames(connection)
      .take(2)
      .subscribe {
        val emission = ++emissions
        events += "start $emission"
        if (emission == 1) {
          connection.sendTableTrigger("author")
        }
        events += "end $emission"
      }

    assertThat(events)
      .containsExactly("start 1", "end 1", "start 2", "end 2")
      .inOrder()
  }

  @Test
  fun `closing the connection completes the stream without running a pending re-run`() {
    withSingleThreadScheduler { connection, _ ->
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      val observer = authorNames(connection)
        .doOnNext {
          entered.countDown()
          release.await()
        }
        .test()
      check(entered.await(5, SECONDS))

      connection.sendTableTrigger("author")
      mockStatic(Log::class.java).use {
        connection.close()
      }
      release.countDown()

      check(observer.await(5, SECONDS))
      observer
        .assertValueCount(1)
        .assertComplete()
    }
  }

  @Test
  fun `observing a closed connection completes without running the query`() {
    for ((label, observe) in mapOf<String, (DbConnectionImpl) -> TestObserver<*>>(
      "table query" to { connection ->
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .usingConnection(connection)
          .observe()
          .runQuery()
          .test()
      },
      "source-free query" to { connection ->
        SELECT
          .COLUMN(8L.asValue())
          .usingConnection(connection)
          .observe()
          .runQuery()
          .test()
      },
      "undeclared raw first row" to { connection ->
        SELECT
          .RAW("SELECT name FROM author")
          .readAs(STRING)
          .usingConnection(connection)
          .takeFirst()
          .observe()
          .test()
      }
    )) {
      val observer = observe(closedConnection(adapterSettings = querySchedulerSettings()))

      observer
        .withTag(label)
        .assertResult()
    }
  }

  @Test
  fun `one-shot reads of a closed connection fail with NoSuchElementException without running the query`() {
    for ((label, observe) in mapOf<String, (DbConnectionImpl) -> TestObserver<*>>(
      "runQueryOnce" to { connection ->
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .usingConnection(connection)
          .observe()
          .runQueryOnce()
          .test()
      },
      "undeclared raw rows" to { connection ->
        SELECT
          .RAW("SELECT name FROM author")
          .readAs(STRING)
          .usingConnection(connection)
          .observe()
          .test()
      }
    )) {
      val observer = observe(closedConnection(adapterSettings = querySchedulerSettings()))

      observer
        .withTag(label)
        .assertFailure(NoSuchElementException::class.java)
    }
  }

  @Test
  fun `notifications pass through the observable assembly hook`() {
    val assembled = mutableListOf<Class<*>>()
    RxJavaPlugins.setOnObservableAssembly { source ->
      assembled += source.javaClass
      source
    }
    try {
      authorNames(testDbConnection(mock<SupportSQLiteDatabase>()))

      assertThat(assembled).containsExactly(QueryNotifications::class.java)
    } finally {
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `completing on an interruptible scheduler does not interrupt the completion callback`() {
    withSingleThreadScheduler(interruptible = true) { connection, _ ->
      val interrupted = SELECT
        .COLUMN(8L.asValue())
        .usingConnection(connection)
        .observe()
        .ignoreElements()
        .toSingle { Thread.currentThread().isInterrupted }
        .blockingGet()

      assertThat(interrupted).isFalse()
    }
  }

  @Test
  fun `notifications run on the connection scheduler or else on io resolved at subscription, whatever subscribeOn`() {
    val executors = listOf("configured", "io").associateWith { name ->
      Executors.newSingleThreadExecutor {
        Thread(it, name).apply { isDaemon = true }
      }
    }
    val notifications = mapOf(
      "configured" to querySchedulerSettings(Schedulers.from(executors.getValue("configured"))),
      "default" to MutableScatterMap()
    ).mapValues { (_, settings) ->
      authorNames(
        DbConnectionImpl(
          database = TestGeneratedDatabase(tableCount = 1),
          dbHelper = RecordingOpenHelper(mock<SupportSQLiteDatabase>()),
          adapterSettings = settings
        )
      )
    }
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.from(executors.getValue("io")) }
    try {
      val threads = notifications.mapValues { (_, notification) ->
        notification
          .subscribeOn(Schedulers.single())
          .map { Thread.currentThread().name }
          .blockingFirst()
      }

      assertThat(threads).isEqualTo(
        mapOf(
          "configured" to "configured",
          "default" to "io"
        )
      )
    } finally {
      RxJavaPlugins.reset()
      executors.values.forEach(ExecutorService::shutdownNow)
    }
  }

  @Test
  fun `observing a query with an unbound parameter fails at the observe call`() {
    val parameter = true.asValue()
      .asParameter("x")
    val plan = SELECT.COLUMN(parameter)
      .WHERE(parameter)
      .compile()

    val failure = assertThrows(IllegalArgumentException::class.java) { plan.observe() }

    assertThat(failure.message).isEqualTo("Missing parameter 'x'")
  }

  private fun authorNames(connection: DbConnectionImpl) = SELECT
    .COLUMN(AUTHOR.NAME)
    .FROM(AUTHOR)
    .usingConnection(connection)
    .observe()

  @Suppress("UnstableApiUsage")
  private fun withSingleThreadScheduler(
    interruptible: Boolean = false,
    block: (DbConnectionImpl, ExecutorService) -> Unit
  ) {
    val scheduled = Executors.newSingleThreadExecutor {
      Thread(it).apply { isDaemon = true }
    }
    try {
      block(
        testDbConnection(
          database = mock<SupportSQLiteDatabase>(),
          adapterSettings = querySchedulerSettings(Schedulers.from(scheduled, interruptible))
        ),
        scheduled
      )
    } finally {
      scheduled.shutdownNow()
    }
  }
}
