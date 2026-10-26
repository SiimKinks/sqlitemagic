package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class JoinSelectTest {
  @Test
  fun `every join kind renders the requested SQL`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val joinSql: String,
      val ids: List<Long?>
    )

    listOf(
      Case(
        label = "LEFT JOIN ON a lifted field",
        query = SELECT.COLUMN(PET.ID.nullable())
          .FROM(PERSON)
          .LEFT_JOIN(PET)
          .ON(PERSON.ID IS PET.OWNER)
          .ORDER_BY(PERSON.ID.ASC(), PET.ID.ASC()),
        joinSql = """ LEFT JOIN "main"."pet" ON "person"."id" = "pet"."owner"""",
        ids = listOf(1L, 2L, 3L, null, null)
      ),
      Case(
        label = "INNER JOIN ON",
        query = SELECT.COLUMN(PET.ID)
          .FROM(PERSON)
          .INNER_JOIN(PET)
          .ON(PERSON.ID IS PET.OWNER)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ INNER JOIN "main"."pet" ON "person"."id" = "pet"."owner"""",
        ids = listOf(1L, 2L, 3L)
      ),
      Case(
        label = "INNER JOIN USING",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .INNER_JOIN(PET)
          .USING(PET.ID)
          .ORDER_BY(PERSON.ID.ASC()),
        joinSql = """ INNER JOIN "main"."pet" USING ("id")""",
        ids = listOf(1L, 2L, 3L)
      ),
      Case(
        label = "bare INNER JOIN",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .INNER_JOIN(PET)
          .WHERE(PERSON.ID IS PET.OWNER)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ INNER JOIN "main"."pet"""",
        ids = listOf(1L, 1L, 2L)
      ),
      Case(
        label = "CROSS JOIN",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .CROSS_JOIN(PET)
          .ORDER_BY(PERSON.ID.ASC(), PET.ID.ASC()),
        joinSql = """ CROSS JOIN "main"."pet"""",
        ids = listOf(1L, 1L, 1L, 2L, 2L, 2L, 3L, 3L, 3L, 4L, 4L, 4L)
      ),
      Case(
        label = "CROSS JOIN ON",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .CROSS_JOIN(PET)
          .ON(PERSON.ID IS PET.OWNER)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ CROSS JOIN "main"."pet" ON "person"."id" = "pet"."owner"""",
        ids = listOf(1L, 1L, 2L)
      ),
      Case(
        label = "NATURAL JOIN on id and name",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .NATURAL_JOIN(PET),
        joinSql = """ NATURAL JOIN "main"."pet"""",
        ids = emptyList()
      ),
      Case(
        label = "NATURAL LEFT JOIN",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .NATURAL_LEFT_JOIN(PET)
          .ORDER_BY(PERSON.ID.ASC()),
        joinSql = """ NATURAL LEFT JOIN "main"."pet"""",
        ids = listOf(1L, 2L, 3L, 4L)
      ),
      Case(
        label = "NATURAL CROSS JOIN",
        query = SELECT.COLUMN(PERSON.ID)
          .FROM(PERSON)
          .NATURAL_CROSS_JOIN(PET),
        joinSql = """ NATURAL CROSS JOIN "main"."pet"""",
        ids = emptyList()
      ),
      Case(
        label = "RIGHT JOIN USING",
        query = SELECT.COLUMN(PERSON.ID.nullable())
          .FROM(PERSON)
          .RIGHT_JOIN(PET)
          .USING(PET.NAME)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ RIGHT JOIN "main"."pet" USING ("name")""",
        ids = listOf(null, null, null)
      ),
      Case(
        label = "bare RIGHT JOIN",
        query = SELECT.COLUMN(PET.ID)
          .FROM(PERSON)
          .RIGHT_JOIN(PET)
          .WHERE(PERSON.ID IS PET.OWNER)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ RIGHT JOIN "main"."pet"""",
        ids = listOf(1L, 2L, 3L)
      ),
      Case(
        label = "FULL JOIN USING",
        query = SELECT.COLUMN(PET.ID.nullable())
          .FROM(PERSON)
          .FULL_JOIN(PET)
          .USING(PET.ID)
          .ORDER_BY(PERSON.ID.ASC()),
        joinSql = """ FULL JOIN "main"."pet" USING ("id")""",
        ids = listOf(1L, 2L, 3L, null)
      ),
      Case(
        label = "bare FULL JOIN",
        query = SELECT.COLUMN(PET.ID.nullable())
          .FROM(PERSON)
          .FULL_JOIN(PET)
          .WHERE(PERSON.ID IS PET.OWNER)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ FULL JOIN "main"."pet"""",
        ids = listOf(1L, 2L, 3L)
      ),
      Case(
        label = "NATURAL RIGHT JOIN",
        query = SELECT.COLUMN(PET.ID)
          .FROM(PERSON)
          .NATURAL_RIGHT_JOIN(PET)
          .ORDER_BY(PET.ID.ASC()),
        joinSql = """ NATURAL RIGHT JOIN "main"."pet"""",
        ids = listOf(1L, 2L, 3L)
      ),
      Case(
        label = "NATURAL FULL JOIN",
        query = SELECT.COLUMN(PET.ID.nullable())
          .FROM(PERSON)
          .NATURAL_FULL_JOIN(PET)
          .ORDER_BY(PERSON.ID.ASC(), PET.ID.ASC()),
        joinSql = """ NATURAL FULL JOIN "main"."pet"""",
        ids = listOf(1L, 2L, 3L, null, null, null, null)
      )
    ).forEach { case ->
      assertWithMessage(case.label)
        .that(case.query.compile().sql)
        .contains("""FROM "main"."person"${case.joinSql}""")
      assertWithMessage(case.label)
        .that(case.query.rowsIn(::petDatabase))
        .isEqualTo(case.ids)
    }
  }

  @Test
  fun `RIGHT and FULL joins keep the unmatched rows of their preserved sides`() {
    val adultOwner = (PERSON.ID IS PET.OWNER) AND (PERSON.AGE GREATER_THAN 40)
    val right = SELECT
      .COLUMNS(PERSON.ID.nullable(), PET.NAME)
      .FROM(PERSON)
      .RIGHT_JOIN(PET)
      .ON(adultOwner)
      .ORDER_BY(PET.ID.ASC())
    val full = SELECT
      .COLUMNS(PERSON.ID.nullable(), PET.NAME.nullable())
      .FROM(PERSON)
      .FULL_JOIN(PET)
      .ON(adultOwner)
      .ORDER_BY(PERSON.ID.ASC(), PET.ID.ASC())

    fun joinSql(kind: String) = """FROM "main"."person" $kind JOIN "main"."pet" ON ("person"."id" = """ +
        """"pet"."owner") AND ("person"."age" > ?)"""

    assertThat(listOf(right.compile(), full.compile()).map(::snapshot)).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """SELECT "person"."id", "pet"."name" ${joinSql("RIGHT")} """ +
              """ORDER BY "pet"."id" ASC""",
          arguments = listOf(NativeValue.Integer(40))
        ),
        PlanSnapshot(
          sql = """SELECT "person"."id", "pet"."name" ${joinSql("FULL")} """ +
              """ORDER BY "person"."id" ASC, "pet"."id" ASC""",
          arguments = listOf(NativeValue.Integer(40))
        )
      )
    )
    assertThat(right.rowsIn(::petDatabase)).isEqualTo(
      listOf(
        Row2(null, "Rex"),
        Row2(null, "Tom"),
        Row2(2L, "Kit")
      )
    )
    assertThat(full.rowsIn(::petDatabase)).isEqualTo(
      listOf(
        Row2(null, "Rex"),
        Row2(null, "Tom"),
        Row2(1L, null),
        Row2(2L, "Kit"),
        Row2(3L, null),
        Row2(4L, null)
      )
    )
  }

  @Test
  fun `FULL joins read the merged USING key through a raw expression`() {
    val mergedId = "id".expr(LONG)
    val ids = SELECT
      .COLUMNS(mergedId, PET.ID.nullable())
      .FROM(PET)
      .FULL_JOIN(PERSON)
      .USING(PERSON.ID)
      .ORDER_BY(mergedId.ASC())

    assertThat(snapshot(ids.compile())).isEqualTo(
      PlanSnapshot(
        sql = """SELECT id, "pet"."id" FROM "main"."pet" FULL JOIN "main"."person" USING ("id") """ +
            "ORDER BY id ASC",
        arguments = emptyList()
      )
    )
    assertThat(ids.rowsIn(::petDatabase)).isEqualTo(
      listOf(
        Row2(1L, 1L),
        Row2(2L, 2L),
        Row2(3L, 3L),
        Row2(4L, null)
      )
    )
  }

  @Test
  fun `fields interpolate into raw SQL as their quoted occurrence reference`() {
    val parent = PERSON AS "parent"
    val children = SELECT.COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .INNER_JOIN(parent)
      .ON(PERSON.PARENT_ID IS parent.ID)
      .WHERE("${parent.AGE} < ?".expr(BOOLEAN, 40))
      .ORDER_BY(PERSON.ID.ASC())
    val names = SELECT.COLUMN(PERSON.NAME)
      .FROM(PERSON) AS "names"

    assertThat(
      listOf(
        "${PERSON.ID}",
        "${parent.NAME}",
        "${PET.NAME.nullable()}",
        "${PET.NAME.nullable() AS "pet_name"}",
        "${PERSON.ID AS "person_id"}",
        "${(PERSON AS """a"b""").ID}",
        "${names[PERSON.NAME]}",
        "${names[PERSON.NAME] AS "name"}"
      )
    ).isEqualTo(
      listOf(
        """"person"."id"""",
        """"parent"."name"""",
        """"pet"."name"""",
        """"pet"."name"""",
        """"person"."id"""",
        """"a""b"."id"""",
        """"names"."person.name"""",
        """"names"."person.name""""
      )
    )
    assertThat(children.compile().sql).endsWith(
      """WHERE "parent"."age" < ? ORDER BY "person"."id" ASC"""
    )
    assertThat(children.rowsIn(::petDatabase)).isEqualTo(listOf("Grace", "Linus"))
  }

  @Test
  fun `entity selections read the root occurrence over joins`() {
    assertThat(
      listOf(
        SELECT FROM PERSON INNER_JOIN PET ON (PERSON.ID IS PET.OWNER) ORDER_BY PET.ID.ASC(),
        (SELECT FROM PERSON LEFT_JOIN PET ON (PERSON.ID IS PET.OWNER) WHERE (PET.ID.IS_NULL())) ORDER_BY
            PERSON.ID.ASC()
      ).map { it.rowsIn(::petDatabase) }
    ).isEqualTo(
      listOf(
        listOf(ada, ada, grace),
        listOf(unnamed, linus)
      )
    )
    assertThat(
      SELECT.FROM(PET)
        .INNER_JOIN(PERSON)
        .ON(PET.OWNER IS PERSON.ID)
        .WHERE(PERSON.NAME IS "Grace")
        .rowsIn(::petDatabase)
    ).isEqualTo(
      listOf(
        Pet(
          id = 3L,
          owner = 2L,
          name = "Kit",
          nickname = null
        )
      )
    )
  }

  @Test
  fun `aliased occurrences join the same table`() {
    val parent = PERSON AS "parent"
    val parents = SELECT
      .COLUMNS(PERSON.NAME, parent.NAME)
      .FROM(PERSON)
      .INNER_JOIN(parent)
      .ON(PERSON.PARENT_ID IS parent.ID)
      .ORDER_BY(PERSON.ID.ASC())

    assertThat(parents.compile().sql).isEqualTo(
      """SELECT "person"."name", "parent"."name" FROM "main"."person" INNER JOIN "main"."person" """ +
          """AS "parent" ON "person"."parent_id" = "parent"."id" ORDER BY "person"."id" ASC"""
    )
    assertThat(parents.rowsIn(::petDatabase)).isEqualTo(
      listOf(
        Row2("Grace", "Ada"),
        Row2(null, "Grace"),
        Row2("Linus", "Linus")
      )
    )
  }

  @Test
  fun `lifted fields read SQL NULL without their decoder`() {
    val child = PERSON AS "child"

    assertThat(
      SELECT
        .COLUMNS(PERSON.ID, child.TOKEN.nullable())
        .FROM(PERSON)
        .LEFT_JOIN(child)
        .ON(PERSON.ID IS child.PARENT_ID)
        .ORDER_BY(PERSON.ID.ASC())
        .rowsIn(::petDatabase)
    ).isEqualTo(
      listOf(
        Row2(1L, Token("g")),
        Row2(2L, Token("n")),
        Row2(3L, null),
        Row2(4L, Token("l"))
      )
    )
  }

  @Test
  fun `outer-joined reads are checked before SQL`() {
    val owner = PERSON AS "owner"
    val pets = PET AS "p"
    fun notNull(field: String) =
      "$field is NOT NULL but an outer join can leave it NULL; select the field with .nullable()"

    fun nullEntity(occurrence: String) =
      "An outer join can leave the $occurrence entity NULL; select its fields with .nullable()"

    val rejected = listOf(
      "LEFT JOIN right side" to SELECT.COLUMN(PET.NAME)
        .FROM(PERSON)
        .LEFT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "LEFT JOIN computed value" to SELECT.COLUMN(PET.ID + 1)
        .FROM(PERSON)
        .LEFT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "LEFT JOIN second output" to SELECT
        .COLUMNS(PERSON.ID, PET.OWNER)
        .FROM(PERSON)
        .LEFT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "NATURAL LEFT JOIN alias" to SELECT.COLUMN(pets.NAME)
        .FROM(PERSON)
        .NATURAL_LEFT_JOIN(pets),
      "RIGHT JOIN left side" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .RIGHT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "RIGHT JOIN prefix" to SELECT.COLUMN(PET.NAME)
        .FROM(PERSON)
        .INNER_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER)
        .RIGHT_JOIN(owner)
        .ON(owner.ID IS PET.OWNER),
      "FULL JOIN left side" to SELECT.COLUMN(PERSON.AGE)
        .FROM(PERSON)
        .FULL_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "FULL JOIN right side" to SELECT.COLUMN(PET.NAME)
        .FROM(PERSON)
        .FULL_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "NATURAL RIGHT JOIN left side" to SELECT.COLUMN(PERSON.ID)
        .FROM(PERSON)
        .NATURAL_RIGHT_JOIN(PET),
      "NATURAL FULL JOIN right side" to SELECT.COLUMN(PET.ID)
        .FROM(PERSON)
        .NATURAL_FULL_JOIN(PET),
      "RIGHT JOIN entity" to SELECT.FROM(PERSON)
        .RIGHT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "FULL JOIN aliased entity" to SELECT.FROM(owner)
        .FULL_JOIN(PET)
    )
    val accepted = listOf(
      "LEFT JOIN nullable field" to SELECT.COLUMN(PET.NICKNAME)
        .FROM(PERSON)
        .LEFT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "LEFT JOIN null test" to SELECT.COLUMN(PET.ID.IS_NULL())
        .FROM(PERSON)
        .LEFT_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER),
      "RIGHT JOIN preserved alias" to SELECT.COLUMN(owner.NAME)
        .FROM(PERSON)
        .INNER_JOIN(PET)
        .ON(PERSON.ID IS PET.OWNER)
        .RIGHT_JOIN(owner)
        .ON(owner.ID IS PET.OWNER)
    )

    assertThat(
      rejected.map { (label, query) ->
        label to assertThrows(IllegalArgumentException::class.java, query::compile).message
      }
    ).isEqualTo(
      listOf(
        "LEFT JOIN right side" to notNull("pet.name"),
        "LEFT JOIN computed value" to notNull("pet.id"),
        "LEFT JOIN second output" to notNull("pet.owner"),
        "NATURAL LEFT JOIN alias" to notNull("p.name"),
        "RIGHT JOIN left side" to notNull("person.id"),
        "RIGHT JOIN prefix" to notNull("pet.name"),
        "FULL JOIN left side" to notNull("person.age"),
        "FULL JOIN right side" to notNull("pet.name"),
        "NATURAL RIGHT JOIN left side" to notNull("person.id"),
        "NATURAL FULL JOIN right side" to notNull("pet.id"),
        "RIGHT JOIN entity" to nullEntity("person"),
        "FULL JOIN aliased entity" to nullEntity("owner")
      )
    )
    assertThat(accepted.map { (label, query) -> label to query.rowsIn(::petDatabase).size }).isEqualTo(
      listOf(
        "LEFT JOIN nullable field" to 5,
        "LEFT JOIN null test" to 5,
        "RIGHT JOIN preserved alias" to 5
      )
    )
  }

  @Test
  fun `joined queries bind in SQL order and observe every table`() {
    val filtered = SELECT.COLUMN(PERSON.ID)
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON((PERSON.ID IS PET.OWNER) AND (PET.NAME IS "Rex"))
      .WHERE(PERSON.AGE GREATER_THAN 18)

    assertThat(observedSnapshot(filtered.compile())).isEqualTo(
      ObservedPlanSnapshot(
        sql = """SELECT "person"."id" FROM "main"."person" LEFT JOIN "main"."pet" ON """ +
            """("person"."id" = "pet"."owner") AND ("pet"."name" = ?) WHERE "person"."age" > ?""",
        arguments = listOf(NativeValue.Text("Rex"), NativeValue.Integer(18)),
        sources = listOf("person", "pet")
      )
    )
    assertThat(
      petDatabase().use { database ->
        filtered
          .usingConnection(database.dbConnection())
          .count()
          .execute()
      }
    ).isEqualTo(2L)
  }
}
