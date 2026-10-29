package com.siimkinks.sqlitemagic

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

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
