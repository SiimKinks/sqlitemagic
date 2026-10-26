package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Assert.assertThrows
import org.junit.Test

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
  val PLACE_CITY = Column<Member, String, SqlNullable, String?, String?, SqlNullable, String?>(
    table = this,
    name = "place_city",
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = Cursor::getString
  )
  val PLACE_ZIP = Column(
    table = this,
    name = "place_zip",
    nullness = SqlNullable,
    parameterEncoding = INT.nullBypassParameterEncoding(),
    selectedRead = nullableRead(Cursor::getInt)
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

  override fun readPartialEntity(cursor: Cursor, positions: IntArray, related: RelatedEntities?) = Member(
    id = cursor.getLong(positions[0]),
    place = when {
      positions[1] < 0 && positions[2] < 0 -> null
      else -> Place(
        city = cursor.getString(positions[1]),
        zip = positions[2].takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt)
      )
    }
  )
}

internal class WideRowSelectTest {
  private val authorFields = """"author"."id", "author"."name", "author"."publisher""""

  @Test
  fun `field lists expand into selected rows read by their expressions`() {
    val nextId = (BOOK.ID + 1L) AS "next_id"
    val query = SELECT
      .COLUMNS(BOOK.TITLE, nextId, AUTHOR.all())
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())

    assertThat(query.compile().sql).isEqualTo(
      """SELECT "book"."title", "book"."id" + ? AS "next_id", $authorFields FROM "main"."book" """ +
          """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" ORDER BY "book"."id" ASC"""
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
    assertThat(error).hasMessageThat().isEqualTo(""""publisher"."name" was not selected""")
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
      val connected = labels.usingConnection(database.dbConnection())
      val expected = listOf("Dune by Ada", "Emma by Grace", "Kernel by Linus")

      assertThat(
        listOf(
          connected.execute(),
          connected
            .observe()
            .runQueryOnce()
            .blockingGet()
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

    BundledSqliteDatabase().use { database ->
      database.execSQL("CREATE TABLE member (id INTEGER PRIMARY KEY, place_city TEXT, place_zip INTEGER)")
      database.execSQL("INSERT INTO member VALUES (1, 'Tartu', NULL)")
      val connection = database.dbConnection()
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
      assertThat(error).hasMessageThat().isEqualTo("""Member.place.city is required; select "member"."place_city"""")
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
