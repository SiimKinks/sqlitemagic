package com.siimkinks.sqlitemagic.coroutines

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.SELECT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

internal class QueryFlowsConcurrencyTest {
  private class Stream {
    val emissions = AtomicInteger()
    val lastEmissionStart = AtomicLong()
  }

  private data class Outcome(
    val streamsEmittingAfterLastChange: Int,
    val streamsDeliveringLaterChanges: Int,
    val hungCancellations: Int
  )

  @Test
  fun `concurrent writers leave every flow current and responsive and cancellation never hangs`() {
    for ((label, writes) in mapOf(
      "serialized 50 us writes" to Writes(
        writers = 4,
        perWriter = 500,
        writeNanos = 50_000,
        serialized = true
      ),
      "back-to-back writes" to Writes(
        writers = 4,
        perWriter = 20_000,
        writeNanos = 0,
        serialized = false
      )
    )) {
      val connection = mockConnection(Dispatchers.Default)
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val streams = List(10) { Stream() }
      val collections = streams.map { stream ->
        scope.launch {
          SELECT
            .COLUMN(AUTHOR.NAME)
            .FROM(AUTHOR)
            .usingConnection(connection)
            .observe()
            .collect {
              stream.lastEmissionStart.set(System.nanoTime())
              stream.emissions.incrementAndGet()
            }
        }
      }
      awaitStreams(streams) { emissions.get() > 0 }

      val lastChange = writes.publish(connection)
      val current = awaitStreams(streams) { lastEmissionStart.get() > lastChange }
      val responsive = (1..3).minOf {
        val before = streams.associateWith { it.emissions.get() }
        connection.sendTableTrigger("author")
        awaitStreams(streams) { emissions.get() > before.getValue(this) }
      }
      val hung = runBlocking {
        collections.count { collection -> withTimeoutOrNull(1.seconds) { collection.cancelAndJoin() } == null }
      }
      scope.cancel()

      assertWithMessage(label)
        .that(
          Outcome(
            streamsEmittingAfterLastChange = current,
            streamsDeliveringLaterChanges = responsive,
            hungCancellations = hung
          )
        )
        .isEqualTo(
          Outcome(
            streamsEmittingAfterLastChange = streams.size,
            streamsDeliveringLaterChanges = streams.size,
            hungCancellations = 0
          )
        )
    }
  }

  private class Writes(
    val writers: Int,
    val perWriter: Int,
    val writeNanos: Long,
    val serialized: Boolean
  ) {
    fun publish(connection: DbConnectionImpl): Long {
      val lastChange = AtomicLong()
      val writeLock = Any()
      val start = CountDownLatch(1)
      val pool = Executors.newFixedThreadPool(writers) {
        Thread(it).apply { isDaemon = true }
      }
      repeat(writers) {
        pool.execute {
          start.await()
          repeat(perWriter) {
            when {
              serialized -> synchronized(writeLock) { write(lastChange) }
              else -> write(lastChange)
            }
            connection.sendTableTrigger("author")
          }
        }
      }
      start.countDown()
      pool.shutdown()
      check(pool.awaitTermination(60, SECONDS))
      return lastChange.get()
    }

    private fun write(lastChange: AtomicLong) {
      val end = System.nanoTime() + writeNanos
      while (System.nanoTime() < end) {
        Thread.onSpinWait()
      }
      lastChange.accumulateAndGet(System.nanoTime(), ::maxOf)
    }
  }

  private fun awaitStreams(
    streams: List<Stream>,
    condition: Stream.() -> Boolean
  ): Int {
    val deadline = System.nanoTime() + SECONDS.toNanos(10)
    while (System.nanoTime() < deadline && !streams.all(condition)) {
      Thread.sleep(5)
    }
    return streams.count(condition)
  }
}
