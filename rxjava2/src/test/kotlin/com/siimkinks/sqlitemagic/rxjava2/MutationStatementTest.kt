package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.ada
import com.siimkinks.sqlitemagic.grace
import com.siimkinks.sqlitemagic.linus
import com.siimkinks.sqlitemagic.people
import com.siimkinks.sqlitemagic.personDatabase
import com.siimkinks.sqlitemagic.persons
import com.siimkinks.sqlitemagic.plus
import com.siimkinks.sqlitemagic.unnamed
import io.reactivex.Single
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
        val connection = database.dbConnection()
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
}
