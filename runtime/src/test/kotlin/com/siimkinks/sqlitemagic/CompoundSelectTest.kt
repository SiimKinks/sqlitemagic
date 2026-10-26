package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class CompoundSelectTest {
  private val everyone = SELECT
    .COLUMN(PERSON.ID)
    .FROM(PERSON)
  private val adults = SELECT
    .COLUMN(PERSON.ID)
    .FROM(PERSON)
    .WHERE(PERSON.AGE GREATER_THAN 20)
  private val owners = SELECT
    .COLUMN(PET.OWNER)
    .FROM(PET)
    .WHERE(PET.NAME IS_NOT "Kit")

  @Test
  fun `operators render in SQL order, bind each arm and apply the tail to the compound`() {
    val union = (adults UNION owners).ORDER_BY(PERSON.ID.ASC())
    val unionAll = (adults UNION_ALL owners).ORDER_BY(PERSON.ID.ASC())
    val intersect = adults INTERSECT owners
    val except = (everyone EXCEPT owners).ORDER_BY(PERSON.ID.ASC())
    val chained = (everyone EXCEPT adults UNION owners).ORDER_BY(PERSON.ID.DESC())
    val paged = (everyone UNION owners)
      .ORDER_BY(PERSON.ID.ASC())
      .LIMIT(2)
      .OFFSET(1)

    assertThat(
      listOf(
        union.compile(),
        chained.compile(),
        paged.compile()
      )
        .map(::snapshot)
    ).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """SELECT "person"."id" FROM "main"."person" WHERE "person"."age" > ? UNION """ +
              """SELECT "pet"."owner" FROM "main"."pet" WHERE "pet"."name" != ? """ +
              """ORDER BY "person"."id" ASC""",
          arguments = listOf(NativeValue.Integer(20L), NativeValue.Text("Kit"))
        ),
        PlanSnapshot(
          sql = """SELECT "person"."id" FROM "main"."person" EXCEPT SELECT "person"."id" FROM """ +
              """"main"."person" WHERE "person"."age" > ? UNION SELECT "pet"."owner" FROM "main"."pet" """ +
              """WHERE "pet"."name" != ? ORDER BY "person"."id" DESC""",
          arguments = listOf(NativeValue.Integer(20L), NativeValue.Text("Kit"))
        ),
        PlanSnapshot(
          sql = """SELECT "person"."id" FROM "main"."person" UNION SELECT "pet"."owner" FROM """ +
              """"main"."pet" WHERE "pet"."name" != ? ORDER BY "person"."id" ASC LIMIT 2 OFFSET 1""",
          arguments = listOf(NativeValue.Text("Kit"))
        )
      )
    )
    petDatabase().use { database ->
      assertThat(
        listOf(
          union.rows(database),
          unionAll.rows(database),
          intersect.rows(database),
          except.rows(database),
          chained.rows(database),
          paged.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(1L, 2L),
          listOf(1L, 1L, 1L, 2L),
          listOf(1L),
          listOf(2L, 3L, 4L),
          listOf(4L, 3L, 1L),
          listOf(2L, 3L)
        )
      )
    }
  }

  @Test
  fun `a nullable scalar arm widens unions and lifted columns let row arms combine`() {
    val pets = SELECT
      .COLUMN(PET.NAME)
      .FROM(PET)
    val people = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(PERSON.AGE LESS_THAN 40)
    val names = (pets UNION people).ORDER_BY(PET.NAME.ASC())
    val petsOnly = (pets EXCEPT people).ORDER_BY(PET.NAME.ASC())
    val petRows = SELECT
      .COLUMNS(PET.OWNER, PET.NAME.nullable())
      .FROM(PET)
    val minorRows = SELECT
      .COLUMNS(PERSON.ID, PERSON.NAME)
      .FROM(PERSON)
      .WHERE(PERSON.AGE LESS_THAN 20)
    val rows = (petRows UNION minorRows).ORDER_BY(PET.OWNER.ASC(), PET.NAME.ASC())

    petDatabase().use { database ->
      assertThat(
        listOf(
          names.rows(database),
          petsOnly.rows(database),
          rows.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(null, "Ada", "Kit", "Linus", "Rex", "Tom"),
          listOf("Kit", "Rex", "Tom"),
          listOf(
            Row2(1L, "Rex"),
            Row2(1L, "Tom"),
            Row2(2L, "Kit"),
            Row2(3L, null),
            Row2(4L, "Linus")
          )
        )
      )
    }
  }

  @Test
  fun `entity compounds read through the left arm and read deeply together`() {
    val minors = SELECT FROM PERSON WHERE (PERSON.AGE LESS_THAN 20)
    val firstPerson = SELECT FROM PERSON WHERE (PERSON.ID IS 1L)
    val minorsOrAda = (minors UNION firstPerson).ORDER_BY(PERSON.ID.ASC())
    val firstBook = SELECT FROM BOOK WHERE (BOOK.ID IS 1L)
    val laterBooks = SELECT FROM BOOK WHERE (BOOK.ID GREATER_THAN 1L)
    val books = (firstBook UNION laterBooks).ORDER_BY(BOOK.ID.ASC())
    val deepBooks = books.queryDeep()
    val mixedDepth = (SELECT FROM BOOK).queryDeep() UNION (SELECT FROM BOOK)

    assertThat(minorsOrAda.compile().sql).isEqualTo(
      """SELECT "person"."id", "person"."name", "person"."age", "person"."parent_id", """ +
          """"person"."token", "person"."flag" FROM "main"."person" WHERE "person"."age" < ? UNION """ +
          """SELECT "person"."id", "person"."name", "person"."age", "person"."parent_id", """ +
          """"person"."token", "person"."flag" FROM "main"."person" WHERE "person"."id" = ? """ +
          """ORDER BY "person"."id" ASC"""
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          minorsOrAda.rows(database),
          books.rows(database),
          deepBooks.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(ada, unnamed, linus),
          books(shallow = true),
          books(shallow = false)
        )
      )
      assertThat(sqliteErrorMessage { mixedDepth.rows(database) })
        .contains("SELECTs to the left and right of UNION do not have the same number of result columns")
    }
  }

  @Test
  fun `compounds nest as queries and a compound right arm renders as a subquery`() {
    val members = adults UNION owners
    val grouped = (everyone EXCEPT (adults UNION owners)).ORDER_BY(PERSON.ID.ASC())
    val inMembers = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(PERSON.ID IN members)
      .ORDER_BY(PERSON.ID.ASC())
    val anyMember = SELECT.COLUMN(EXISTS(members))
    val derived = members AS "m"
    val fromDerived = SELECT
      .COLUMN(derived[PERSON.ID])
      .FROM(derived)
      .ORDER_BY(derived[PERSON.ID].DESC())
    val labels = members
      .ORDER_BY(PERSON.ID.ASC())
      .mapTo { "#$it" }

    assertThat(
      listOf(
        grouped.compile().sql,
        fromDerived.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "person"."id" FROM "main"."person" EXCEPT SELECT * FROM (SELECT "person"."id" """ +
            """FROM "main"."person" WHERE "person"."age" > ? UNION SELECT "pet"."owner" """ +
            """FROM "main"."pet" WHERE "pet"."name" != ?) ORDER BY "person"."id" ASC""",
        """SELECT "m"."person.id" FROM (SELECT "person"."id" AS "person.id" FROM "main"."person" """ +
            """WHERE "person"."age" > ? UNION SELECT "pet"."owner" FROM "main"."pet" """ +
            """WHERE "pet"."name" != ?) AS "m" ORDER BY "m"."person.id" DESC"""
      )
    )
    petDatabase().use { database ->
      assertThat(
        listOf(
          grouped.rows(database),
          inMembers.rows(database),
          anyMember.rows(database),
          fromDerived.rows(database),
          labels.rows(database),
          listOf(
            (adults UNION_ALL owners)
              .usingConnection(database.dbConnection())
              .count()
              .execute()
          )
        )
      ).isEqualTo(
        listOf(
          listOf(3L, 4L),
          listOf("Ada", "Grace"),
          listOf(true),
          listOf(2L, 1L),
          listOf("#1", "#2"),
          listOf(4L)
        )
      )
    }
  }

  @Test
  fun `a right arm with its own ordering and limit renders as a subquery`() {
    val latest = everyone
      .ORDER_BY(PERSON.ID.DESC())
      .LIMIT(1)
    val compound = adults UNION latest

    assertThat(compound.compile().sql).isEqualTo(
      """SELECT "person"."id" FROM "main"."person" WHERE "person"."age" > ? UNION SELECT * FROM (""" +
          """SELECT "person"."id" FROM "main"."person" ORDER BY "person"."id" DESC LIMIT 1)"""
    )
    petDatabase().use { database ->
      assertThat(compound.rows(database)).containsExactly(1L, 2L, 4L)
    }
  }

  @Test
  fun `compounds inline literals in view definitions and SQLite owns arm clauses`() {
    val view = GeneratedView(
      viewName = "member_ids",
      temporary = false,
      query = { (adults UNION owners).ORDER_BY(PERSON.ID.ASC()) },
      columns = listOf("person.id")
    )
    val orderedArm = (everyone.ORDER_BY(PERSON.ID.ASC())) UNION owners

    assertThat(view.definition.sql).isEqualTo(
      """SELECT "person"."id" AS "person.id" FROM "main"."person" WHERE "person"."age" > 20 UNION """ +
          """SELECT "pet"."owner" FROM "main"."pet" WHERE "pet"."name" != 'Kit' """ +
          """ORDER BY "person"."id" ASC"""
    )
    petDatabase().use { database ->
      val connected = adults.usingConnection(database.dbConnection())
      assertThat(assertThrows(IllegalArgumentException::class.java) { connected UNION owners })
        .hasMessageThat()
        .isEqualTo("A compound's arms cannot use a connection; choose it on the compound")
      database.createViews(view)
      assertThat(
        SELECT
          .RAW("""SELECT "person.id" FROM member_ids""")
          .readAs(LONG)
          .usingConnection(database.dbConnection())
          .execute()
      ).isEqualTo(listOf(1L, 2L))
      assertThat(sqliteErrorMessage { orderedArm.rows(database) })
        .contains("ORDER BY clause should come after UNION not before")
    }
  }
}
