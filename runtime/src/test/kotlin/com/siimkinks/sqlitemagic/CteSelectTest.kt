package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class CteSelectTest {
  private val personId = PERSON.ID.AS("person_id")

  @Test
  fun `a WITH declares each input once under its name and reads it through exports or its default result`() {
    val adults = WITH(
      SELECT.COLUMN(personId)
        .FROM(PERSON)
        .WHERE(PERSON.AGE GREATER_THAN 20)
    ) { ids ->
      SELECT
        .COLUMN(ids[personId])
        .FROM(ids)
        .ORDER_BY(ids[personId].DESC())
    }
    val minors = WITH(SELECT FROM PERSON WHERE (PERSON.AGE LESS_THAN 20) AS "minors") { minors ->
      (SELECT FROM minors).ORDER_BY(minors[PERSON.ID].ASC())
    }
    val pairs = WITH(
      SELECT.COLUMN(personId)
        .FROM(PERSON) AS "cte1",
      SELECT.COLUMN(PET.OWNER)
        .FROM(PET)
    ) { ids, owners ->
      SELECT
        .COLUMN(ids[personId])
        .FROM(ids)
        .WHERE(
          ids[personId] IN SELECT
            .COLUMN(owners[PET.OWNER])
            .FROM(owners)
        )
    }

    assertThat(
      listOf(
        adults.compile(),
        minors.compile(),
        pairs.compile()
      )
        .map(::snapshot)
    ).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """WITH "cte1" AS (SELECT "person"."id" AS "person_id" FROM "main"."person" """ +
              """WHERE "person"."age" > ?) SELECT "cte1"."person_id" FROM "cte1" """ +
              """ORDER BY "cte1"."person_id" DESC""",
          arguments = listOf(NativeValue.Integer(20L))
        ),
        PlanSnapshot(
          sql = """WITH "minors" AS (SELECT "person"."id" AS "person.id", """ +
              """"person"."name" AS "person.name", "person"."age" AS "person.age", """ +
              """"person"."parent_id" AS "person.parent_id", """ +
              """"person"."token" AS "person.token", "person"."flag" AS "person.flag" """ +
              """FROM "main"."person" WHERE "person"."age" < ?) SELECT "minors"."person.id", """ +
              """"minors"."person.name", "minors"."person.age", "minors"."person.parent_id", """ +
              """"minors"."person.token", "minors"."person.flag" FROM "minors" """ +
              """ORDER BY "minors"."person.id" ASC""",
          arguments = listOf(NativeValue.Integer(20L))
        ),
        PlanSnapshot(
          sql = """WITH "cte1" AS (SELECT "person"."id" AS "person_id" FROM "main"."person"), """ +
              """"cte2" AS (SELECT "pet"."owner" AS "pet.owner" FROM "main"."pet") """ +
              """SELECT "cte1"."person_id" FROM "cte1" WHERE "cte1"."person_id" IN """ +
              """(SELECT "cte2"."pet.owner" FROM "cte2")""",
          arguments = emptyList()
        )
      )
    )
    petDatabase().use { database ->
      assertThat(
        listOf(
          adults.rows(database),
          minors.rows(database),
          pairs.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(2L, 1L),
          listOf(unnamed, linus),
          listOf(1L, 2L)
        )
      )
    }
  }

  @Test
  fun `handles declare in the order given so a later CTE reads an earlier one`() {
    val owner = PET.OWNER.AS("owner")
    val owners = SELECT
      .COLUMN(owner)
      .FROM(PET)
      .asCte()
    val ownerNameQuery = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(
        PERSON.ID IN SELECT
          .COLUMN(owners[owner])
          .FROM(owners)
      )
    val ownerNames = (ownerNameQuery AS "owner_names").asCte()
    val query = WITH(owners, ownerNames) {
      SELECT
        .COLUMN(ownerNames[PERSON.NAME])
        .FROM(ownerNames)
        .ORDER_BY(ownerNames[PERSON.NAME].ASC())
    }

    assertThat(query.compile().sql).isEqualTo(
      """WITH "cte1" AS (SELECT "pet"."owner" AS "owner" FROM "main"."pet"), "owner_names" AS """ +
          """(SELECT "person"."name" AS "person.name" FROM "main"."person" WHERE "person"."id" IN """ +
          """(SELECT "cte1"."owner" FROM "cte1")) SELECT "owner_names"."person.name" FROM "owner_names" """ +
          """ORDER BY "owner_names"."person.name" ASC"""
    )
    assertThat(query.observedTables()).isEqualTo(listOf("pet", "person"))
    petDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(listOf("Ada", "Grace"))
    }
  }

  @Test
  fun `WITH leads compounds, self joins and nested queries`() {
    val everyone = SELECT.COLUMN(PERSON.ID)
      .FROM(PERSON)
    val owners = SELECT.COLUMN(PET.OWNER)
      .FROM(PET)
    val compound = WITH(everyone.WHERE(PERSON.AGE GREATER_THAN 20)) { adults ->
      val adultIds = SELECT.COLUMN(adults[PERSON.ID])
        .FROM(adults)
      (adultIds UNION owners).ORDER_BY(adults[PERSON.ID].ASC())
    }
    val pairs = WITH(everyone) { ids ->
      val other = ids AS "other"
      SELECT
        .COLUMNS(ids[PERSON.ID], other[PERSON.ID])
        .FROM(ids)
        .INNER_JOIN(other)
        .ON(other[PERSON.ID] IS ids[PERSON.ID] + 1L)
        .ORDER_BY(ids[PERSON.ID].ASC())
    }
    val petOwners = WITH(owners) { pets ->
      SELECT
        .COLUMN(pets[PET.OWNER])
        .FROM(pets)
    }
    val withArm = everyone EXCEPT petOwners
    val ownerNames = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(
        PERSON.ID IN WITH(owners) { pets ->
          SELECT
            .COLUMN(pets[PET.OWNER])
            .FROM(pets)
        }
      )
      .ORDER_BY(PERSON.ID.ASC())

    assertThat(
      listOf(
        compound.compile().sql,
        pairs.compile().sql,
        ownerNames.compile().sql,
        withArm.compile().sql
      )
    ).isEqualTo(
      listOf(
        """WITH "cte1" AS (SELECT "person"."id" AS "person.id" FROM "main"."person" """ +
            """WHERE "person"."age" > ?) SELECT "cte1"."person.id" FROM "cte1" UNION """ +
            """SELECT "pet"."owner" FROM "main"."pet" ORDER BY "cte1"."person.id" ASC""",
        """WITH "cte1" AS (SELECT "person"."id" AS "person.id" FROM "main"."person") """ +
            """SELECT "cte1"."person.id", "other"."person.id" FROM "cte1" INNER JOIN "cte1" AS "other" """ +
            """ON "other"."person.id" = ("cte1"."person.id" + ?) ORDER BY "cte1"."person.id" ASC""",
        """SELECT "person"."name" FROM "main"."person" WHERE "person"."id" IN (WITH "cte1" AS """ +
            """(SELECT "pet"."owner" AS "pet.owner" FROM "main"."pet") SELECT "cte1"."pet.owner" """ +
            """FROM "cte1") ORDER BY "person"."id" ASC""",
        """SELECT "person"."id" FROM "main"."person" EXCEPT SELECT * FROM (WITH "cte1" AS """ +
            """(SELECT "pet"."owner" AS "pet.owner" FROM "main"."pet") SELECT "cte1"."pet.owner" """ +
            """FROM "cte1")"""
      )
    )
    petDatabase().use { database ->
      assertThat(
        listOf(
          compound.rows(database),
          pairs.rows(database),
          ownerNames.rows(database),
          withArm
            .ORDER_BY(PERSON.ID.ASC())
            .rows(database),
          listOf(
            compound
              .usingConnection(database.dbConnection())
              .count()
              .execute()
          )
        )
      ).isEqualTo(
        listOf(
          listOf(1L, 2L),
          listOf(
            Row2(1L, 2L),
            Row2(2L, 3L),
            Row2(3L, 4L)
          ),
          listOf("Ada", "Grace"),
          listOf(3L, 4L),
          listOf(2L)
        )
      )
    }
  }

  @Test
  fun `view definitions inline literals and SQLite reports an undeclared CTE`() {
    val view = GeneratedView(
      viewName = "adult_ids",
      temporary = false,
      query = {
        WITH(
          SELECT.COLUMN(personId)
            .FROM(PERSON)
            .WHERE(PERSON.AGE GREATER_THAN 20)
        ) { ids ->
          SELECT
            .COLUMN(ids[personId])
            .FROM(ids)
        }
      },
      columns = listOf("person_id")
    )
    val owners = SELECT.COLUMN(PET.OWNER)
      .FROM(PET)
      .asCte()
    val undeclared = SELECT FROM owners

    assertThat(
      listOf(
        view.definition.sql,
        undeclared.compile().sql
      )
    ).isEqualTo(
      listOf(
        """WITH "cte1" AS (SELECT "person"."id" AS "person_id" FROM "main"."person" """ +
            """WHERE "person"."age" > 20) SELECT "cte1"."person_id" AS "person_id" FROM "cte1"""",
        """SELECT * FROM "cte1""""
      )
    )
    petDatabase().use { database ->
      database.createViews(view)
      assertThat(
        SELECT
          .RAW("SELECT person_id FROM adult_ids ORDER BY person_id")
          .readAs(LONG)
          .usingConnection(database.dbConnection())
          .execute()
      ).isEqualTo(listOf(1L, 2L))
      assertThat(sqliteErrorMessage { undeclared.rows(database) }).isEqualTo("no such table: cte1")
    }
  }
}
