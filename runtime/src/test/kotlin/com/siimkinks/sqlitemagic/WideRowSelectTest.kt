package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.nullableReadType
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Assert.assertThrows
import org.junit.Test

private val decodedNickname = nullableReadType(
  storage = SqlStorage.STRING,
  encode = { value: String -> value },
  decode = { value -> value ?: "none" },
  encodingNullness = SqlNullable
).let { type ->
  Column(
    table = PET,
    name = "nickname",
    nullness = SqlNullable,
    parameterEncoding = type.parameterEncoding(),
    selectedRead = type.selectRead
  )
}

private data class Place(
  val city: String,
  val zip: Int?
)

private data class Member(
  val id: Long,
  val place: Place?
)

private class MemberTable : Table<Member>(
  name = "member",
  alias = null
) {
  val ID = UniqueColumn<Member, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val PLACE_CITY = Column(
    table = this,
    name = "place_city",
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getText)
  )
  val PLACE_ZIP = Column(
    table = this,
    name = "place_zip",
    nullness = SqlNullable,
    parameterEncoding = INT.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getInt)
  )

  override val entityFields = listOf(ID, PLACE_CITY, PLACE_ZIP)

  override val entityProperties = listOf(
    EntityProperty(
      path = "Member.id",
      required = true
    ),
    EntityProperty(
      path = "Member.place.city",
      required = true,
      within = "Member.place"
    ),
    EntityProperty(
      path = "Member.place.zip",
      required = false
    )
  )

  override fun readPartialEntity(statement: SQLiteStatement, positions: IntArray, related: RelatedEntities?) = Member(
    id = statement.getLong(positions[0]),
    place = when {
      positions[1] < 0 && positions[2] < 0 -> null
      else -> Place(
        city = statement.getText(positions[1]),
        zip = positions[2]
          .takeIf { it >= 0 && !statement.isNull(it) }
          ?.let(statement::getInt)
      )
    }
  )
}

internal class WideRowSelectTest {
  @Test
  fun `field lists expand into selected rows read by their expressions`() {
    val nextId = (BOOK.ID + 1L) AS "next_id"
    val query = SELECT
      .COLUMNS(BOOK.TITLE, nextId, AUTHOR.all())
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())

    assertThat(query.compile().sql).isEqualTo(
      """SELECT "book"."title", "book"."id" + ? AS "next_id", "author"."id", "author"."name", """ +
          """"author"."publisher" FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = "author"."id" """ +
          """ORDER BY "book"."id" ASC"""
    )
    val rows = query.rowsIn(::libraryDatabase)
    assertThat(
      rows.map { row ->
        listOf(row[BOOK.TITLE], row[nextId], row[AUTHOR.NAME], row[AUTHOR.PUBLISHER])
      }
    ).isEqualTo(
      listOf(
        listOf("Dune", 2L, "Ada", 1L),
        listOf("Emma", 3L, "Grace", null),
        listOf("Kernel", 4L, "Linus", 2L)
      )
    )
    val error = assertThrows(IllegalArgumentException::class.java) { rows.first()[PUBLISHER.NAME] }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo(""""publisher"."name" was not selected""")
  }

  @Test
  fun `nullable field lists read the unmatched rows of an outer join as null`() {
    val query = SELECT
      .COLUMNS(PERSON.ID, PET.all().nullable())
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON(PERSON.ID IS PET.OWNER)
      .ORDER_BY(PERSON.ID.ASC(), PET.ID.ASC())
    val expected = listOf(
      listOf(1L, 1L, "Rex", null),
      listOf(1L, 2L, "Tom", "Tommy"),
      listOf(2L, 3L, "Kit", null),
      listOf(3L, null, null, null),
      listOf(4L, null, null, null)
    )
    val read = { row: SelectedRow ->
      listOf(row[PERSON.ID], row[PET.ID.nullable()], row[PET.NAME.nullable()], row[PET.NICKNAME])
    }
    val rows = query.rowsIn(::petDatabase)

    assertThat(
      listOf(
        rows.map(read),
        query
          .mapTo(read)
          .rowsIn(::petDatabase)
      )
    ).isEqualTo(listOf(expected, expected))
    val error = assertThrows(IllegalArgumentException::class.java) { rows.first()[PET.NAME] }
    assertThat(error)
      .hasMessageThat()
      .isEqualTo(""""pet"."name" is selected as nullable; read it with .nullable()""")
  }

  @Test
  fun `nullable field lists keep their alias in either order and plain ones reject an outer join`() {
    val aliasLast = SELECT
      .COLUMNS(PET.all().nullable() AS "animal")
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON(PERSON.ID IS PET.OWNER)
    val aliasFirst = SELECT
      .COLUMNS((PET.all() AS "animal").nullable())
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON(PERSON.ID IS PET.OWNER)
    val plain = SELECT
      .COLUMNS(PET.all() AS "animal")
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON(PERSON.ID IS PET.OWNER)

    assertThat(listOf(aliasLast, aliasFirst).map { it.compile().sql }).isEqualTo(
      List(size = 2) {
        """SELECT "pet"."id" AS "animal.id", "pet"."owner" AS "animal.owner", "pet"."name" AS "animal.name", """ +
            """"pet"."nickname" AS "animal.nickname" FROM "main"."person" """ +
            """LEFT JOIN "main"."pet" ON "person"."id" = "pet"."owner""""
      }
    )
    assertThat(assertThrows(IllegalArgumentException::class.java, plain::compile))
      .hasMessageThat()
      .isEqualTo("pet.id is NOT NULL but an outer join can leave it NULL; select the field with .nullable()")
  }

  @Test
  fun `a subquery exports the columns of a nullable field list`() {
    val pets = SELECT
      .COLUMNS(PERSON.ID, PET.all().nullable())
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON(PERSON.ID IS PET.OWNER) AS "pets"

    assertThat(
      SELECT
        .COLUMNS(pets[PET.NAME.nullable()], pets[PET.NICKNAME])
        .FROM(pets)
        .ORDER_BY(pets[PERSON.ID].ASC(), pets[PET.ID.nullable()].ASC())
        .rowsIn(::petDatabase)
    ).isEqualTo(
      listOf(
        Row2("Rex", null),
        Row2("Tom", "Tommy"),
        Row2("Kit", null),
        Row2(null, null),
        Row2(null, null)
      )
    )
    assertThat(assertThrows(IllegalArgumentException::class.java) { pets[PET.NAME] })
      .hasMessageThat()
      .isEqualTo("""pets exports "pet"."name" as nullable; read it with .nullable()""")
  }

  @Test
  fun `a subquery exports each aliased output of one expression by its own name`() {
    val name = PET.NAME.nullable()
    val first = name AS "first"
    val second = name AS "second"
    val pets = SELECT
      .COLUMNS(first, second)
      .FROM(PET) AS "pets"

    assertThat(
      SELECT
        .COLUMNS(pets[first], pets[second])
        .FROM(pets)
        .compile()
        .sql
    ).isEqualTo(
      """SELECT "pets"."first", "pets"."second" FROM """ +
          """(SELECT "pet"."name" AS "first", "pet"."name" AS "second" FROM "main"."pet") AS "pets""""
    )
  }

  @Test
  fun `owned and live rows read a decoded column and its lift alike`() {
    val query = SELECT
      .COLUMNS(decodedNickname, decodedNickname.nullable(), PET.all())
      .FROM(PET)
      .ORDER_BY(PET.ID.ASC())
    val read = { row: SelectedRow -> listOf(row[decodedNickname], row[decodedNickname.nullable()]) }
    val expected = listOf(
      listOf("none", null),
      listOf("Tommy", "Tommy"),
      listOf("none", null)
    )

    assertThat(
      listOf(
        query
          .rowsIn(::petDatabase)
          .map(read),
        query
          .mapTo(read)
          .rowsIn(::petDatabase)
      )
    ).isEqualTo(listOf(expected, expected))
  }

  @Test
  fun `owned and live rows find a value only by its selected instance`() {
    data class Case(
      val label: String,
      val selected: SqlExpression<*, *, *>,
      val read: SqlExpression<*, *, *>,
      val failure: String
    )

    val cases = listOf(
      Case(
        label = "a nullable column read with nullable()",
        selected = decodedNickname,
        read = decodedNickname.nullable(),
        failure = "was not selected"
      ),
      Case(
        label = "a lifted nullable column read without nullable()",
        selected = decodedNickname.nullable(),
        read = decodedNickname,
        failure = "is selected as nullable; read it with .nullable()"
      ),
      Case(
        label = "a raw expression of the same SQL and another type",
        selected = "upper(name)".expr(STRING.nullable),
        read = "upper(name)".expr(LONG.nullable),
        failure = "was not selected"
      ),
      Case(
        label = "a raw expression created again",
        selected = "upper(name)".expr(STRING.nullable),
        read = "upper(name)".expr(STRING.nullable),
        failure = "was not selected"
      ),
      Case(
        label = "a computed lift created again",
        selected = (PET.ID + 1L).nullable(),
        read = (PET.ID + 1L).nullable(),
        failure = "was not selected"
      )
    )

    assertThat(
      cases.map { case ->
        val query = SELECT
          .COLUMNS(case.selected, PET.all())
          .FROM(PET)
        case.label to listOf(
          assertThrows(IllegalArgumentException::class.java) {
            query
              .rowsIn(::petDatabase)
              .first()[case.read]
          },
          assertThrows(IllegalArgumentException::class.java) {
            query
              .mapTo { row -> row[case.read] }
              .rowsIn(::petDatabase)
          }
        ).map(IllegalArgumentException::message)
      }
    ).isEqualTo(
      cases.map { case ->
        case.label to List(size = 2) { "${case.read} ${case.failure}" }
      }
    )
  }

  @Test
  fun `a mapping callback reads one live row per execution`() {
    val rows = mutableListOf<SelectedRow>()
    val labels = SELECT
      .COLUMNS(BOOK.TITLE, AUTHOR.all())
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())
      .mapTo { row ->
        rows += row
        "${row[BOOK.TITLE]} by ${row[AUTHOR.NAME]}"
      }

    libraryDatabase().use { database ->
      val connected = labels.usingConnection(database.connection)
      val expected = listOf("Dune by Ada", "Emma by Grace", "Kernel by Linus")

      assertThat(
        listOf(
          connected.execute(),
          connected
            .compile()
            .observation()
            .query
            .runBlocking()
        )
      ).isEqualTo(listOf(expected, expected))
    }
    assertThat(
      rows
        .chunked(3)
        .map { execution -> execution.count { row -> row === execution.first() } }
    ).isEqualTo(listOf(3, 3))
    assertThat(rows[0]).isNotSameInstanceAs(rows[3])
  }

  @Test
  fun `mapTo with a table reads partial entities with the relationships their fields reach`() {
    val shallow = SELECT
      .COLUMNS(BOOK.ID, BOOK.TITLE, AUTHOR.all())
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())
      .mapTo(BOOK)
    val deep = SELECT
      .COLUMNS(BOOK.all(), AUTHOR.all(), PUBLISHER.all())
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())
      .mapTo(BOOK)
    val authors = SELECT
      .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
      .FROM(AUTHOR)
      .ORDER_BY(AUTHOR.ID.ASC())
      .mapTo(AUTHOR)

    assertThat(shallow.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = true))
    assertThat(deep.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = false))
    assertThat(authors.rowsIn(::libraryDatabase)).isEqualTo(
      books(shallow = true).map { it.author.copy(publisher = null) }
    )
  }

  @Test
  fun `an embedded value's required field is required only when the value is selected`() {
    val member = MemberTable()
    val withoutPlace = SELECT
      .COLUMN(member.ID)
      .FROM(member)
      .mapTo(member)
    val withPlace = SELECT
      .COLUMNS(member.ID, member.PLACE_CITY, member.PLACE_ZIP)
      .FROM(member)
      .mapTo(member)
    val partialPlace = SELECT
      .COLUMNS(member.ID, member.PLACE_ZIP)
      .FROM(member)
      .mapTo(member)

    TestDatabase().use { database ->
      database.execSQL("CREATE TABLE member (id INTEGER PRIMARY KEY, place_city TEXT, place_zip INTEGER)")
      database.execSQL("INSERT INTO member VALUES (1, 'Tartu', NULL)")
      val connection = database.connection
      assertThat(
        listOf(withoutPlace, withPlace).map {
          it.usingConnection(connection)
            .execute()
        }
      ).isEqualTo(
        listOf(
          listOf(
            Member(
              id = 1L,
              place = null
            )
          ),
          listOf(
            Member(
              id = 1L,
              place = Place(
                city = "Tartu",
                zip = null
              )
            )
          )
        )
      )
      val error = assertThrows(IllegalArgumentException::class.java) {
        partialPlace
          .usingConnection(connection)
          .execute()
      }
      assertThat(error)
        .hasMessageThat()
        .isEqualTo("""Member.place.city is required; select "member"."place_city"""")
    }
  }

  @Test
  fun `partial mappings missing required or unreachable values reject before SQL`() {
    val cases = listOf(
      SELECT
        .COLUMN(BOOK.ID)
        .FROM(BOOK)
        .mapTo(BOOK) to """Book.title is required; select "book"."title"""",
      SELECT
        .COLUMNS(BOOK.ID, BOOK.TITLE)
        .FROM(BOOK)
        .mapTo(BOOK) to "Book.author is required; select its author fields",
      SELECT
        .COLUMNS(BOOK.ID, BOOK.TITLE, AUTHOR.ID)
        .FROM(BOOK)
        .mapTo(BOOK) to """Author.name is required; select "author"."name"""",
      SELECT
        .COLUMNS(BOOK.all(), (BOOK.ID + 1L) AS "next_id")
        .FROM(BOOK)
        .mapTo(BOOK) to "mapTo(book) reads plain fields, but selected value 4 is computed",
      SELECT
        .COLUMNS(PUBLISHER.ID, PUBLISHER.NAME, PERSON.NAME)
        .FROM(PUBLISHER)
        .INNER_JOIN(PERSON)
        .ON(PERSON.ID IS PUBLISHER.ID)
        .mapTo(PUBLISHER) to """"person"."name" is not a field of publisher or of a table its relationships reach""",
      SELECT
        .COLUMN(BOOK.TITLE)
        .FROM(BOOK)
        .mapTo(PERSON) to "mapTo(person) needs person among the query's sources",
      SELECT
        .COLUMN(1L.asValue())
        .mapTo(BOOK) to "mapTo(book) needs a FROM"
    )

    assertThat(
      cases.map { (query, _) ->
        assertThrows(IllegalArgumentException::class.java, query::compile).message
      }
    ).isEqualTo(cases.map(Pair<*, String>::second))
  }
}
