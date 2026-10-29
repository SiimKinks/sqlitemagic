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
    personDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = querySchedulerSettings())
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
      val cursors = SELECT
        .RAW("PRAGMA table_info('person')")
        .usingConnection(connection)
        .toCursor()
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
      cursors
        .assertComplete()
        .values()
        .single()
        .use { cursor ->
          assertThat(cursor.isClosed).isFalse()
          assertThat(cursor.count).isEqualTo(6)
        }
      streamed.dispose()
      firstStreamed.dispose()
    }
  }

  @Test
  fun `undeclared observation rejects subscription in a transaction`() {
    personDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = querySchedulerSettings())
      val raw = SELECT
        .RAW("SELECT 1")
        .readAs(LONG)
        .usingConnection(connection)
      val transaction = connection.newTransaction()
      val failures = try {
        mapOf(
          "rows" to raw
            .observe()
            .test(),
          "first" to raw
            .takeFirst()
            .observe()
            .test(),
          "cursor" to raw
            .toCursor()
            .observe()
            .test()
        ).mapValues { (_, observer) ->
          observer
            .assertNoValues()
            .errors()
            .map { failure -> failure.javaClass to failure.message }
        }
      } finally {
        transaction.end()
      }

      assertThat(failures).isEqualTo(
        listOf("rows", "first", "cursor").associateWith {
          listOf(IllegalStateException::class.java to "Cannot subscribe to observable query in a transaction.")
        }
      )
    }
  }
}
