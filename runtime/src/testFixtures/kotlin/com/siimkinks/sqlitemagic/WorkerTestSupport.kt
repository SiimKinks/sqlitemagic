package com.siimkinks.sqlitemagic

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal fun withWorkers(block: (ExecutorService) -> Unit) {
  val workers = Executors.newFixedThreadPool(2) {
    Thread(it).apply { isDaemon = true }
  }
  try {
    block(workers)
  } finally {
    workers.shutdownNow()
    check(workers.awaitTermination(5, SECONDS)) { "Workers did not stop" }
  }
}

internal class Stream {
  val emissions = AtomicInteger()
  val lastEmissionStart = AtomicLong()
}

internal class Writes(
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

internal fun awaitStreams(
  streams: List<Stream>,
  condition: Stream.() -> Boolean
): Int {
  val deadline = System.nanoTime() + SECONDS.toNanos(10)
  while (System.nanoTime() < deadline && !streams.all(condition)) {
    Thread.sleep(5)
  }
  return streams.count(condition)
}
