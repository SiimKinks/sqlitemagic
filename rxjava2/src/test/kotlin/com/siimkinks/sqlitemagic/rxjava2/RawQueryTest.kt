package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.personDatabase
import org.junit.Test

internal class RawQueryTest {
  @Test
  fun `declared queries stream while undeclared ones run once`() {
    personDatabase(adapterSettings = querySchedulerSettings()).use { database ->
      val connection = database.connection
      val names = SELECT
        .RAW("SELECT name FROM person WHERE age < 18 ORDER BY id")
        .readAs(PERSON.NAME)
        .usingConnection(connection)
      val streamed = names
        .observes(PERSON)
        .observe()
        .runQuery()
        .test()
      val firstStreamed = names
        .observes(PERSON)
        .takeFirst()
        .observe()
        .runQuery()
        .test()
      val once = names
        .observe()
        .test()
      val emptyFirst = SELECT
        .RAW("SELECT name FROM person WHERE id < 0")
        .readAs(STRING)
        .usingConnection(connection)
        .takeFirst()
        .observe()
        .test()
      UPDATE
        .RAW("UPDATE person SET name = ? WHERE id = ?", "Kid", 3L)
        .notifies(PERSON)
        .usingConnection(connection)
        .execute()

      assertThat(streamed.values()).isEqualTo(listOf(listOf(null, "Linus"), listOf("Kid", "Linus")))
      streamed.assertNotComplete()
      assertThat(firstStreamed.values()).isEqualTo(listOf("Kid"))
      assertThat(once.values()).isEqualTo(listOf(listOf(null, "Linus")))
      once.assertComplete()
      emptyFirst
        .assertNoValues()
        .assertComplete()
      streamed.dispose()
      firstStreamed.dispose()
    }
  }

  @Test
  fun `undeclared observation rejects subscription in a transaction`() {
    personDatabase(adapterSettings = querySchedulerSettings()).use { database ->
      val connection = database.connection
      val raw = SELECT
        .RAW("SELECT 1")
        .readAs(LONG)
        .usingConnection(connection)
      val transaction = connection.newTransaction()
      val observers = try {
        mapOf(
          "rows" to raw
            .observe()
            .test(),
          "first" to raw
            .takeFirst()
            .observe()
            .test()
        )
      } finally {
        transaction.end()
      }

      observers.forEach { (label, observer) ->
        observer
          .withTag(label)
          .assertNoValues()
          .assertError { failure ->
            failure.javaClass == IllegalStateException::class.java &&
                failure.message == "Cannot subscribe to observable query in a transaction."
          }
      }
    }
  }
}
