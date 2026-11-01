package com.siimkinks.sqlitemagic.coroutines

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Stream
import com.siimkinks.sqlitemagic.Writes
import com.siimkinks.sqlitemagic.awaitStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.time.Duration.Companion.seconds

internal class QueryFlowsConcurrencyTest {
  private data class Outcome(
    val streamsEmittingAfterLastChange: Int,
    val streamsDeliveringLaterChanges: Int,
    val hungCancellations: Int
  )

  @Test
  fun `concurrent writers leave every flow current and responsive and cancellation never hangs`() {
    val writePatterns = mapOf(
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
    )
    val flows = mapOf(
      "observe()" to {
        val connection = testConnection(Dispatchers.Default)
        connection to SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .usingConnection(connection)
          .observe()
          .map { System.nanoTime() }
      },
      "observe().runQuery() querying on Dispatchers.IO" to {
        val connection = queryingConnection(
          answers = ::readsAuthor,
          adapterSettings = dispatcherSettings(Dispatchers.IO)
        ) {
          val start = System.nanoTime()
          mock<SQLiteStatement> {
            on { step() } doReturn true
            on { getLong(0) } doReturn start
          }
        }
        connection to SELECT
          .FROM(AUTHOR)
          .usingConnection(connection)
          .count()
          .observe()
          .runQuery()
      }
    )
    for ((writesLabel, writes) in writePatterns) {
      for ((flowLabel, flow) in flows) {
        val (connection, emissionStarts) = flow()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val streams = List(10) { Stream() }
        val collections = streams.map { stream ->
          scope.launch {
            emissionStarts.collect { start ->
              stream.lastEmissionStart.set(start)
              stream.emissions.incrementAndGet()
            }
          }
        }
        awaitStreams(streams) { emissions.get() > 0 }

        val lastChange = writes.publish(connection)
        val current = awaitStreams(streams) { lastEmissionStart.get() > lastChange }
        val responsive = (1..3).minOf {
          val before = streams.associateWith { stream -> stream.emissions.get() }
          connection.sendTableTrigger("author")
          awaitStreams(streams) { emissions.get() > before.getValue(this) }
        }
        val hung = runBlocking {
          collections.count { collection -> withTimeoutOrNull(1.seconds) { collection.cancelAndJoin() } == null }
        }
        scope.cancel()

        assertWithMessage("$flowLabel, $writesLabel")
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
  }
}
