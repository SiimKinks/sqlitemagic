package com.siimkinks.sqlitemagic.rxjava3

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.INSERT
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.Person
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.Token
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.ada
import com.siimkinks.sqlitemagic.grace
import com.siimkinks.sqlitemagic.linus
import com.siimkinks.sqlitemagic.people
import com.siimkinks.sqlitemagic.personDatabase
import com.siimkinks.sqlitemagic.persons
import com.siimkinks.sqlitemagic.plus
import com.siimkinks.sqlitemagic.unnamed
import io.reactivex.rxjava3.core.Single
import org.junit.Test

internal class MutationStatementTest {
  @Test
  fun `observe is cold and executes on every subscription`() {
    for ((label, observe) in mapOf<String, (DbConnection) -> Single<Int>>(
      "statement" to { connection ->
        (UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.AGE + 1) WHERE (PERSON.ID IS 1L))
          .usingConnection(connection)
          .observe()
      },
      "raw statement" to { connection ->
        UPDATE
          .RAW("UPDATE person SET age = age + 1 WHERE id = ?", 1L)
          .usingConnection(connection)
          .observe()
      }
    )) {
      personDatabase().use { database ->
        val connection = database.connection
        val birthday = observe(connection)
        val before = connection.persons()

        birthday
          .test()
          .assertResult(1)
        birthday
          .test()
          .assertResult(1)

        assertWithMessage(label)
          .that(before to connection.persons())
          .isEqualTo(people to listOf(ada.copy(age = 38), grace, unnamed, linus))
      }
    }
  }

  @Test
  fun `observe of readRowId emits the row ID, or completes empty when nothing was inserted`() {
    personDatabase().use { database ->
      val connection = database.connection
      val insert = INSERT
        .OR(CONFLICT_IGNORE)
        .INTO(PERSON)
        .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
        .VALUES(5L, 2, Token("y"))
        .readRowId()
        .usingConnection(connection)
      val inserted = insert.observe()
      val ignored = insert
        .compile()
        .observe()

      inserted
        .test()
        .assertResult(5L)
      ignored
        .test()
        .assertResult()

      assertThat(connection.persons())
        .isEqualTo(
          people + Person(
            id = 5L,
            name = null,
            age = 2,
            parentId = null,
            token = Token("y"),
            flag = null
          )
        )
    }
  }
}
