package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import org.junit.Test

internal class TableObserversTest {
  @Test
  fun `publish reaches registrations whose tables intersect the changed set`() {
    val observers = TableObservers()
    val recorders = mapOf(
      "author" to arrayOf("author"),
      "book" to arrayOf("book"),
      "author or book" to arrayOf("book", "author"),
      "publisher or series" to arrayOf("publisher", "series"),
      "all tables" to null
    ).mapValues { (_, tables) ->
      TriggerRecorder().also { recorder ->
        observers.add(
          tables = tables,
          observer = recorder
        )
      }
    }

    observers.publish(setOf("author", "note"))

    assertThat(recorders.mapValues { (_, recorder) -> recorder.triggers }).isEqualTo(
      mapOf(
        "author" to listOf(setOf("author", "note")),
        "book" to emptyList(),
        "author or book" to listOf(setOf("author", "note")),
        "publisher or series" to emptyList(),
        "all tables" to listOf(setOf("author", "note"))
      )
    )
  }

  @Test
  fun `a closed registration stops receiving while the others continue`() {
    val observers = TableObservers()
    val closing = TriggerRecorder()
    val remaining = TriggerRecorder()
    val registration = observers.add(
      tables = arrayOf("author"),
      observer = closing
    )
    observers.add(
      tables = arrayOf("author"),
      observer = remaining
    )

    observers.publish(setOf("author"))
    registration.close()
    registration.close()
    observers.publish(setOf("author"))

    assertThat(listOf(closing.triggers, remaining.triggers)).isEqualTo(
      listOf(
        listOf(setOf("author")),
        listOf(setOf("author"), setOf("author"))
      )
    )
  }

  @Test
  fun `close notifies every registration once and ends delivery`() {
    val observers = TableObservers()
    val recorders = List(2) {
      TriggerRecorder().also { recorder ->
        observers.add(
          tables = arrayOf("author"),
          observer = recorder
        )
      }
    }

    val closes = listOf(observers.close(), observers.close())
    observers.publish(setOf("author"))

    assertThat(closes).containsExactly(true, false).inOrder()
    assertThat(observers.isClosed).isTrue()
    assertThat(recorders.map(TriggerRecorder::closeCalls)).containsExactly(1, 1)
    assertThat(recorders.flatMap(TriggerRecorder::triggers)).isEmpty()
  }

  @Test
  fun `adding after close reports closed at once and returns a registration that is safe to close`() {
    val observers = TableObservers()
    observers.close()
    val recorder = TriggerRecorder()

    val registration = observers.add(
      tables = arrayOf("author"),
      observer = recorder
    )
    observers.publish(setOf("author"))
    registration.close()

    assertThat(recorder.closeCalls).isEqualTo(1)
    assertThat(recorder.triggers).isEmpty()
  }

  @Test
  fun `registrations changed during a publish take effect from the next publish`() {
    val observers = TableObservers()
    val removed = TriggerRecorder()
    val added = TriggerRecorder()
    lateinit var removedRegistration: AutoCloseable
    observers.add(
      tables = arrayOf("author"),
      observer = object : TableChangeObserver {
        private var publishes = 0

        override fun onTablesChanged(tables: Set<String>) {
          if (publishes++ == 0) {
            removedRegistration.close()
            observers.add(
              tables = arrayOf("author"),
              observer = added
            )
          }
        }

        override fun onClosed() = Unit
      }
    )
    removedRegistration = observers.add(
      tables = arrayOf("author"),
      observer = removed
    )

    observers.publish(setOf("author"))
    observers.publish(setOf("book", "author"))

    assertThat(listOf(removed.triggers, added.triggers)).isEqualTo(
      listOf(
        listOf(setOf("author")),
        listOf(setOf("book", "author"))
      )
    )
  }
}
