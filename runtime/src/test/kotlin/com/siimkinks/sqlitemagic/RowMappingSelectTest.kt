package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.LONG
import org.junit.Test

internal class RowMappingSelectTest {
  private data class PersonSummary(
    val id: Long,
    val name: String?,
    val age: Int
  )

  private data class PersonRecord(
    val id: Long,
    val name: String?,
    val age: Int,
    val parentId: Long?,
    val token: Token,
    val flag: Boolean?,
    val nameAgain: String?,
    val ageAgain: Int,
    val idAgain: Long
  )

  @Test
  fun `three through nine expressions read into rows in selection order`() {
    val row3 = SELECT
      .COLUMNS(PERSON.ID, PERSON.NAME, PERSON.AGE)
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val row9 = SELECT
      .COLUMNS(
        PERSON.ID,
        PERSON.NAME,
        PERSON.AGE,
        PERSON.PARENT_ID,
        PERSON.TOKEN,
        PERSON.FLAG,
        PERSON.NAME AS "name_again",
        PERSON.AGE,
        PERSON.ID
      )
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())

    assertThat(row3.rowsIn(::personDatabase)).isEqualTo(
      people.map { person ->
        Row3(person.id, person.name, person.age)
      }
    )
    assertThat(row9.rowsIn(::personDatabase)).isEqualTo(
      people.map { person ->
        Row9(
          person.id,
          person.name,
          person.age,
          person.parentId,
          person.token,
          person.flag,
          person.name,
          person.age,
          person.id
        )
      }
    )
  }

  @Test
  fun `mapTo builds results without changing the SQL`() {
    val summaries = SELECT
      .COLUMNS(PERSON.ID, PERSON.NAME, PERSON.AGE)
      .FROM(PERSON)
      .WHERE(PERSON.AGE GREATER_THAN 17)
      .ORDER_BY(PERSON.ID.ASC())
    val mapped = summaries.mapTo(::PersonSummary)
    val ids = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
      .mapTo(::MappedId)
    val readIds = SELECT
      .COLUMN(PERSON.AGE)
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
      .readAs(LONG)
      .mapTo(::MappedId)
    val labels = SELECT
      .COLUMNS(PERSON.ID, PERSON.NAME)
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
      .mapTo { id, name -> name ?: "#$id" }

    assertThat(mapped.compile().sql).isEqualTo(summaries.compile().sql)
    assertThat(mapped.rowsIn(::personDatabase)).isEqualTo(
      listOf(ada, grace).map { person ->
        PersonSummary(
          id = person.id,
          name = person.name,
          age = person.age
        )
      }
    )
    assertThat(ids.rowsIn(::personDatabase)).isEqualTo(people.map { MappedId(it.id) })
    assertThat(readIds.rowsIn(::personDatabase)).isEqualTo(people.map { MappedId(it.age.toLong()) })
    assertThat(labels.rowsIn(::personDatabase)).isEqualTo(listOf("Ada", "Grace", "#3", "Linus"))
  }

  @Test
  fun `every result shape reads its rows through every terminal`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val rows: List<Any?>
    )

    data class Terminals(
      val list: List<Any?>,
      val first: Any?,
      val count: Long,
      val cursor: List<Any?>,
      val observed: List<Any?>
    )

    val records = SELECT
      .COLUMNS(
        PERSON.ID,
        PERSON.NAME,
        PERSON.AGE,
        PERSON.PARENT_ID,
        PERSON.TOKEN,
        PERSON.FLAG,
        PERSON.NAME,
        PERSON.AGE,
        PERSON.ID
      )
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.DESC())
    val expectedRecords = people
      .reversed()
      .map { person ->
        PersonRecord(
          id = person.id,
          name = person.name,
          age = person.age,
          parentId = person.parentId,
          token = person.token,
          flag = person.flag,
          nameAgain = person.name,
          ageAgain = person.age,
          idAgain = person.id
        )
      }

    libraryDatabase().use { database ->
      val connection = database.dbConnection()
      val cases = listOf(
        Case(
          label = "scalar",
          query = SELECT.COLUMN(PERSON.NAME)
            .FROM(PERSON)
            .WHERE(PERSON.AGE LESS_THAN 18)
            .ORDER_BY(PERSON.ID.ASC())
            .usingConnection(connection),
          rows = listOf(null, "Linus")
        ),
        Case(
          label = "distinct row",
          query = SELECT
            .DISTINCT(PERSON.AGE, PERSON.AGE AS "again", PERSON.AGE AS "once_more")
            .FROM(PERSON)
            .ORDER_BY(PERSON.AGE.ASC())
            .usingConnection(connection),
          rows = listOf(17, 36, 85).map { age -> Row3(age, age, age) }
        ),
        Case(
          label = "row mapped before choosing its connection",
          query = records
            .mapTo(::PersonRecord)
            .usingConnection(connection),
          rows = expectedRecords
        ),
        Case(
          label = "row mapped after choosing its connection",
          query = records
            .usingConnection(connection)
            .mapTo(::PersonRecord),
          rows = expectedRecords
        ),
        Case(
          label = "wide row callback",
          query = SELECT
            .COLUMNS(BOOK.TITLE, AUTHOR.all())
            .FROM(BOOK)
            .ORDER_BY(BOOK.ID.ASC())
            .mapTo { row -> "${row[BOOK.TITLE]} by ${row[AUTHOR.NAME]}" }
            .usingConnection(connection),
          rows = listOf("Dune by Ada", "Emma by Grace", "Kernel by Linus")
        ),
        Case(
          label = "distinct wide row",
          query = SELECT
            .DISTINCT(AUTHOR.PUBLISHER.nullable(), PUBLISHER.all())
            .FROM(AUTHOR)
            .INNER_JOIN(PUBLISHER)
            .ON(AUTHOR.PUBLISHER IS PUBLISHER.ID)
            .ORDER_BY(PUBLISHER.ID.ASC())
            .mapTo { row -> row[PUBLISHER.NAME] }
            .usingConnection(connection),
          rows = listOf("Tor", null)
        ),
        Case(
          label = "entity",
          query = SELECT.FROM(PERSON)
            .ORDER_BY(PERSON.ID.ASC())
            .usingConnection(connection),
          rows = people
        ),
        Case(
          label = "deep entity",
          query = (SELECT FROM BOOK ORDER_BY BOOK.ID.ASC())
            .queryDeep()
            .usingConnection(connection),
          rows = books(shallow = false)
        )
      )

      cases.forEach { case ->
        val compiledCursor = case.query.toCursor()
        assertWithMessage(case.label)
          .that(
            Terminals(
              list = case.query.execute(),
              first = case.query
                .takeFirst()
                .execute(),
              count = case.query
                .count()
                .execute(),
              cursor = compiledCursor
                .execute()
                .use { rows ->
                  buildList {
                    while (rows.moveToNext()) {
                      add(compiledCursor.getFromCurrentPosition(rows))
                    }
                  }
                },
              observed = checkNotNull(
                case.query
                  .compile()
                  .observation()
                  .query
                  .runBlocking()
              )
            )
          )
          .isEqualTo(
            Terminals(
              list = case.rows,
              first = case.rows.first(),
              count = case.rows.size.toLong(),
              cursor = case.rows,
              observed = case.rows
            )
          )
      }
    }
  }
}
