package com.siimkinks.sqlitemagic.coroutines

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.INSERT
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.Token
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
      personDatabase(adapterSettings = dispatcherSettings()).use { database ->
        val connection = database.connection
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

  @Test
  fun `await of readRowId returns the row ID, or null when nothing was inserted`() {
    personDatabase(adapterSettings = dispatcherSettings()).use { database ->
      val insert = INSERT
        .OR(CONFLICT_IGNORE)
        .INTO(PERSON)
        .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
        .VALUES(5L, 2, Token("y"))
        .readRowId()
        .usingConnection(database.connection)

      val rowIds = runBlocking {
        listOf(
          insert.await(),
          insert
            .compile()
            .await()
        )
      }

      assertThat(rowIds).isEqualTo(listOf(5L, null))
    }
  }
}
