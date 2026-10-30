package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import org.junit.Test

internal class TableObserversTest {
  @Test
  fun `publish notifies each registration whose tables intersect the changed tables once`() {
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

    observers.publish("author")
    observers.publish("note")
    observers.publish(changed("book", "author", "note"))

    assertThat(recorders.mapValues { (_, recorder) -> recorder.triggers }).isEqualTo(
      mapOf(
        "author" to listOf(setOf("author"), setOf("book", "author", "note")),
        "book" to listOf(setOf("book", "author", "note")),
        "author or book" to listOf(setOf("author"), setOf("book", "author", "note")),
        "publisher or series" to emptyList(),
        "all tables" to listOf(setOf("author"), setOf("note"), setOf("book", "author", "note"))
      )
    )
  }

  @Test
  fun `publishing an observed table delivers the same changed set every time`() {
    val observers = TableObservers()
    val delivered = mutableListOf<Set<String>>()
    observers.add(
      tables = arrayOf("author"),
      observer = object : TableChangeObserver {
        override fun onTablesChanged(tables: Set<String>) {
          delivered += tables
        }

        override fun onClosed() = Unit
      }
    )

    observers.publish("author")
    observers.publish("author")

    assertThat(delivered).containsExactly(setOf("author"), setOf("author"))
    assertThat(delivered[1]).isSameInstanceAs(delivered[0])
  }

  @Test
  fun `a closed registration stops receiving while the others continue`() {
    val observers = TableObservers()
    val closing = TriggerRecorder()
    val closingAllTables = TriggerRecorder()
    val remaining = TriggerRecorder()
    val registrations = listOf(
      observers.add(
        tables = arrayOf("author", "book"),
        observer = closing
      ),
      observers.add(
        tables = null,
        observer = closingAllTables
      )
    )
    observers.add(
      tables = arrayOf("author"),
      observer = remaining
    )

    observers.publish("author")
    registrations.forEach(AutoCloseable::close)
    registrations.forEach(AutoCloseable::close)
    observers.publish("author")
    observers.publish("book")
    observers.publish(changed("author", "book"))

    assertThat(listOf(closing.triggers, closingAllTables.triggers, remaining.triggers)).isEqualTo(
      listOf(
        listOf(setOf("author")),
        listOf(setOf("author")),
        listOf(setOf("author"), setOf("author"), setOf("author", "book"))
      )
    )
  }

  @Test
  fun `close notifies every registration once and ends delivery`() {
    val observers = TableObservers()
    val recorders = listOf(arrayOf("author"), arrayOf("author"), arrayOf("author", "book"), null).map { tables ->
      TriggerRecorder().also { recorder ->
        observers.add(
          tables = tables,
          observer = recorder
        )
      }
    }

    val closes = listOf(observers.close(), observers.close())
    observers.publish("author")
    observers.publish(changed("author", "book"))

    assertThat(closes).containsExactly(true, false).inOrder()
    assertThat(observers.isClosed).isTrue()
    assertThat(recorders.map(TriggerRecorder::closeCalls)).containsExactly(1, 1, 1, 1)
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
    observers.publish("author")
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

    observers.publish("author")
    observers.publish(changed("book", "author"))

    assertThat(listOf(removed.triggers, added.triggers)).isEqualTo(
      listOf(
        listOf(setOf("author")),
        listOf(setOf("book", "author"))
      )
    )
  }

  private fun changed(vararg tables: String) = MutableScatterSet<String>().apply { addAll(tables) }
}
