package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

internal class EntitySelectTest {
  @Test
  fun `entity queries read every row through the table reader with clauses`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val adults = (SELECT FROM PERSON) WHERE (PERSON.AGE GREATER_THAN 18)
      val alias = PERSON.AS("p")
      val minors = SELECT.FROM(alias)
        .WHERE(alias.AGE LESS_THAN 18)
        .ORDER_BY(alias.ID.DESC())
        .usingConnection(connection)
      val page = SELECT.FROM(PERSON)
        .ORDER_BY(PERSON.ID.ASC())
        .LIMIT(2)
        .OFFSET(1)
        .usingConnection(connection)

      assertThat(
        adults.ORDER_BY(PERSON.ID.ASC())
          .usingConnection(connection)
          .execute()
      ).isEqualTo(listOf(ada, grace))
      assertThat(minors.execute()).isEqualTo(listOf(linus, unnamed))
      assertThat(page.execute()).isEqualTo(listOf(grace, unnamed))
    }
  }

  @Test
  fun `entity selection renders every field of its occurrence`() {
    val alias = PERSON.AS("p")
    val plan = SELECT.DISTINCT
      .FROM(alias)
      .compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT DISTINCT "p"."id", "p"."name", "p"."age", "p"."parent_id", "p"."token", "p"."flag" """ +
            """FROM "main"."person" AS "p"""",
        arguments = emptyList()
      )
    )
  }

  @Test
  fun `DISTINCT entities compare every entity column`() {
    personDatabase().use { database ->
      database.execSQL("INSERT INTO person VALUES (5, 'Ada', 36, NULL, 'db:a', 1)")
      val connection = database.dbConnection()
      val distinctAdas = (SELECT.DISTINCT FROM PERSON)
        .WHERE(PERSON.NAME IS "Ada")
        .ORDER_BY(PERSON.ID.ASC())
        .usingConnection(connection)

      assertThat(distinctAdas.execute()).isEqualTo(listOf(ada, ada.copy(id = 5L)))
      assertThat(distinctAdas.count().execute()).isEqualTo(2L)
    }
  }

  @Test
  fun `tables without a default entity read fail before any SQL runs`() {
    val readerless = testTable<Unit>(name = "plain")
    val connection = mock<DbConnectionImpl>()

    val failure = assertThrows(IllegalStateException::class.java) {
      SELECT.FROM(readerless)
        .usingConnection(connection)
        .execute()
    }

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("plain has no generated entity reader")
    verifyNoInteractions(connection)
  }
}
