package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Stream
import com.siimkinks.sqlitemagic.Writes
import com.siimkinks.sqlitemagic.awaitStreams
import com.siimkinks.sqlitemagic.testDbConnection
import io.reactivex.schedulers.Schedulers
import org.junit.Test

internal class QueryNotificationsConcurrencyTest {
  private data class Outcome(
    val streamsEmittingAfterLastChange: Int,
    val streamsDeliveringLaterChanges: Int,
    val hungDisposals: Int
  )

  @Test
  fun `concurrent writers leave every stream current and responsive and disposal never hangs`() {
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
      val connection = testDbConnection(adapterSettings = querySchedulerSettings(Schedulers.io()))
      val streams = List(10) { Stream() }
      val subscriptions = streams.map { stream ->
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .usingConnection(connection)
          .observe()
          .subscribe {
            stream.lastEmissionStart.set(System.nanoTime())
            stream.emissions.incrementAndGet()
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
      val hung = subscriptions.count { subscription ->
        Thread(subscription::dispose)
          .apply {
            isDaemon = true
            start()
            join(1_000)
          }
          .isAlive
      }

      assertWithMessage(label)
        .that(
          Outcome(
            streamsEmittingAfterLastChange = current,
            streamsDeliveringLaterChanges = responsive,
            hungDisposals = hung
          )
        )
        .isEqualTo(
          Outcome(
            streamsEmittingAfterLastChange = streams.size,
            streamsDeliveringLaterChanges = streams.size,
            hungDisposals = 0
          )
        )
    }
  }
}
