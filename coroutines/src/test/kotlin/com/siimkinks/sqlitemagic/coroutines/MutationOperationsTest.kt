package com.siimkinks.sqlitemagic.coroutines

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.ada
import com.siimkinks.sqlitemagic.personDatabase
import com.siimkinks.sqlitemagic.plus
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class MutationOperationsTest {
  @Test
  fun `await executes the statement and refreshes observers of its table`() {
    for ((label, await) in mapOf<String, suspend (DbConnection) -> Int>(
      "statement" to { connection ->
        (UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.AGE + 1) WHERE (PERSON.ID IS 1L))
          .usingConnection(connection)
          .await()
      },
      "compiled statement" to { connection ->
        (UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.AGE + 1) WHERE (PERSON.ID IS 1L))
          .usingConnection(connection)
          .compile()
          .await()
      },
      "raw statement" to { connection ->
        UPDATE
          .RAW("UPDATE person SET age = age + 1 WHERE id = ?", 1L)
          .notifies(PERSON)
          .usingConnection(connection)
          .await()
      }
    )) {
      personDatabase().use { database ->
        val connection = database.dbConnection(adapterSettings = dispatcherSettings())
        val outcome = runBlocking {
          val ages = collectUnconfined(
            SELECT
              .COLUMN(PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 1L)
              .usingConnection(connection)
              .observe()
              .runQuery()
          )
          val updated = await(connection)
          ages.cancel()
          updated to ages.signals
        }

        assertWithMessage(label)
          .that(outcome)
          .isEqualTo(1 to FlowSignals(values = listOf(listOf(ada.age), listOf(ada.age + 1))))
      }
    }
  }
}
