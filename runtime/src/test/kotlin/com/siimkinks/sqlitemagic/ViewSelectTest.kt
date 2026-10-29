@file:Suppress("TestFunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.ViewBlock
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Assert.assertThrows
import org.junit.Test

private data class SeriesEnding(
  val name: String,
  val lastBook: Book?
)

private data class SummaryCard(val summary: AuthorSummary)

private data class BookCard(
  val title: String,
  val book: Book
)

private class SeriesEndingTable(alias: String? = null) : Table<SeriesEnding>(
  name = "series_ending",
  alias = alias,
  generatedView = SERIES_ENDING_VIEW
) {
  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = SeriesEnding(
    name = cursor.getString(startIndex),
    lastBook = checkNotNull(related).readOrNull(cursor, 0) as Book?
  )

  override infix fun AS(alias: String) = SeriesEndingTable(alias)
}

private val SERIES_ENDING_VIEW = GeneratedView(
  viewName = "series_ending",
  temporary = false,
  query = { SELECT FROM SERIES },
  columns = listOf("series.name"),
  blocks = listOf(
    ViewBlock(
      key = "last_book",
      target = BOOK
    )
  )
)

private val SERIES_ENDING = SeriesEndingTable()

private class SummaryCardTable(alias: String? = null) : Table<SummaryCard>(
  name = "summary_card",
  alias = alias,
  generatedView = SUMMARY_CARD_VIEW
) {
  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = SummaryCard(
    summary = checkNotNull(related).read(cursor, 0) as AuthorSummary
  )

  override infix fun AS(alias: String) = SummaryCardTable(alias)
}

private val SUMMARY_CARD_VIEW = GeneratedView(
  viewName = "summary_card",
  temporary = false,
  query = { SELECT FROM AUTHOR_SUMMARY },
  columns = emptyList(),
  blocks = listOf(
    ViewBlock(
      key = "author_summary",
      target = AUTHOR_SUMMARY
    )
  )
)

private val SUMMARY_CARD = SummaryCardTable()

private class BookCardTable(
  private val view: GeneratedView,
  alias: String? = null
) : Table<BookCard>(
  name = view.viewName,
  alias = alias,
  generatedView = view
) {
  val TITLE = Column<BookCard, String, SqlNullable, String, String, Required, String>(
    table = this,
    name = "book.title",
    nullness = SqlNullable,
    parameterEncoding = STRING.parameterEncoding(),
    selectedRead = Cursor::getString
  )

  override val entityFields = listOf(TITLE)

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = BookCard(
    title = cursor.getString(startIndex),
    book = checkNotNull(related).read(cursor, 0) as Book
  )

  override infix fun AS(alias: String) = BookCardTable(
    view = view,
    alias = alias
  )
}

private fun bookCardView(viewName: String, query: SelectQuery<*, *>) = GeneratedView(
  viewName = viewName,
  temporary = false,
  query = { query },
  columns = listOf("book.title"),
  blocks = listOf(
    ViewBlock(
      key = "book",
      target = BOOK
    )
  )
)

internal class ViewSelectTest {
  private fun BundledSqliteDatabase.viewNames() = SELECT
    .RAW("SELECT name FROM sqlite_master WHERE type = 'view'")
    .readAs(STRING)
    .usingConnection(dbConnection())
    .execute()

  private fun bookCards(shallow: Boolean) = books(shallow = shallow).map { book ->
    BookCard(
      title = book.title,
      book = book
    )
  }

  @Test
  fun `definition writes values as literals and names field outputs by occurrence`() {
    val summaries = SELECT FROM AUTHOR_SUMMARY ORDER_BY AUTHOR_SUMMARY.AUTHOR_ID.ASC()
    val peer = AUTHOR_SUMMARY AS "peer"

    assertThat(AUTHOR_SUMMARY_VIEW.definition.sql).isEqualTo(
      """SELECT "author"."id" AS "author.id", "author"."name" AS "name" FROM "main"."author" """ +
          """WHERE "author"."name" LIKE '%a%'"""
    )
    assertThat(summaries.compile().sql).isEqualTo(
      """SELECT "author_summary"."author.id", "author_summary"."name" FROM "main"."author_summary" """ +
          """ORDER BY "author_summary"."author.id" ASC"""
    )
    assertThat(summaries.observedTables()).isEqualTo(listOf("author"))
    libraryDatabase().use { database ->
      database.createViews(AUTHOR_SUMMARY_VIEW)
      val connection = database.dbConnection()
      assertThat(summaries.usingConnection(connection).execute()).isEqualTo(
        listOf(
          AuthorSummary(
            id = 1L,
            name = "Ada"
          ),
          AuthorSummary(
            id = 2L,
            name = "Grace"
          )
        )
      )
      assertThat(
        SELECT
          .COLUMN(peer.NAME)
          .FROM(peer)
          .WHERE(peer.AUTHOR_ID IS 2L)
          .usingConnection(connection)
          .execute()
      ).isEqualTo(listOf("Grace"))
    }
  }

  @Test
  fun `table blocks read the related blocks their definition selects`() {
    val shallowView = bookCardView(
      viewName = "book_card",
      query = SELECT FROM BOOK
    )
    val deepView = bookCardView(
      viewName = "deep_book_card",
      query = (SELECT FROM BOOK).queryDeep()
    )
    val shallow = SELECT FROM BookCardTable(shallowView)
    val deep = SELECT FROM BookCardTable(deepView)

    assertThat(shallowView.definition.sql).isEqualTo(
      """SELECT "book"."id" AS "book.id", "book"."title" AS "book.title", """ +
          """"book"."author" AS "book.author", "author"."id" AS "author.id", """ +
          """"author"."name" AS "author.name", "author"."publisher" AS "author.publisher" """ +
          """FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""
    )
    assertThat(shallow.compile().sql).isEqualTo(
      """SELECT "book_card"."book.title", "book_card"."book.id", "book_card"."book.title", """ +
          """"book_card"."book.author", "book_card"."author.id", "book_card"."author.name", """ +
          """"book_card"."author.publisher" FROM "main"."book_card""""
    )
    assertThat(deep.compile().sql).isEqualTo(
      """SELECT "deep_book_card"."book.title", "deep_book_card"."book.id", "deep_book_card"."book.title", """ +
          """"deep_book_card"."book.author", "deep_book_card"."author.id", "deep_book_card"."author.name", """ +
          """"deep_book_card"."author.publisher", "deep_book_card"."publisher.id", """ +
          """"deep_book_card"."publisher.name" FROM "main"."deep_book_card""""
    )
    libraryDatabase().use { database ->
      database.createViews(shallowView, deepView)
      val connection = database.dbConnection()
      assertThat(shallow.usingConnection(connection).execute()).isEqualTo(bookCards(shallow = true))
      assertThat(deep.usingConnection(connection).execute()).isEqualTo(bookCards(shallow = false))
    }
  }

  @Test
  fun `a keyed field list names its block without aliasing the occurrence`() {
    val b = BOOK AS "b"
    val view = bookCardView(
      viewName = "keyed_book_card",
      query = SELECT
        .COLUMNS(b.all() AS "book", AUTHOR.all())
        .FROM(b)
    )
    val wide = SELECT
      .COLUMNS(BOOK.all() AS "entry", AUTHOR.NAME)
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())
      .mapTo { row -> row[BOOK.TITLE] to row[AUTHOR.NAME] }

    assertThat(
      listOf(
        view.definition.sql,
        wide.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "b"."id" AS "book.id", "b"."title" AS "book.title", "b"."author" AS "book.author", """ +
            """"author"."id" AS "author.id", "author"."name" AS "author.name", """ +
            """"author"."publisher" AS "author.publisher" FROM "main"."book" AS "b" """ +
            """LEFT JOIN "main"."author" ON "b"."author" = "author"."id"""",
        """SELECT "book"."id" AS "entry.id", "book"."title" AS "entry.title", """ +
            """"book"."author" AS "entry.author", "author"."name" FROM "main"."book" """ +
            """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" ORDER BY "book"."id" ASC"""
      )
    )
    libraryDatabase().use { database ->
      database.createViews(view)
      val connection = database.dbConnection()
      assertThat(
        listOf(
          (SELECT FROM BookCardTable(view))
            .usingConnection(connection)
            .execute(),
          wide
            .usingConnection(connection)
            .execute()
        )
      ).isEqualTo(
        listOf(
          bookCards(shallow = true),
          listOf("Dune" to "Ada", "Emma" to "Grace", "Kernel" to "Linus")
        )
      )
    }
  }

  @Test
  fun `an unselected required relationship block rejects before DDL`() {
    val view = bookCardView(
      viewName = "book_card",
      query = SELECT
        .COLUMNS(BOOK.ID, BOOK.TITLE)
        .FROM(BOOK)
    )

    libraryDatabase().use { database ->
      val error = assertThrows(IllegalArgumentException::class.java) {
        database.createViews(AUTHOR_SUMMARY_VIEW, view)
      }
      assertThat(error).hasMessageThat().isEqualTo(
        "Cannot create view 'book_card': book.author needs the columns of the author its definition joins through it"
      )
      assertThat(database.viewNames()).isEmpty()
    }
  }

  @Test
  fun `a nullable block reads null when all its columns are NULL`() {
    libraryDatabase().use { database ->
      database.createViews(SERIES_ENDING_VIEW)
      assertThat(
        (SELECT FROM SERIES_ENDING)
          .usingConnection(database.dbConnection())
          .execute()
      ).isEqualTo(
        listOf(
          SeriesEnding(
            name = "Classics",
            lastBook = books(shallow = true)[1]
          ),
          SeriesEnding(
            name = "Solo",
            lastBook = null
          )
        )
      )
    }
  }

  @Test
  fun `view field lists and partial mappings read the view's columns`() {
    val rows = SELECT
      .COLUMNS(BOOK.TITLE, AUTHOR_SUMMARY.all())
      .FROM(BOOK)
      .INNER_JOIN(AUTHOR_SUMMARY)
      .ON(BOOK.AUTHOR IS AUTHOR_SUMMARY.AUTHOR_ID)
      .ORDER_BY(BOOK.ID.ASC())
    val summaries = SELECT
      .COLUMNS(AUTHOR_SUMMARY.NAME, AUTHOR_SUMMARY.AUTHOR_ID)
      .FROM(AUTHOR_SUMMARY)
      .ORDER_BY(AUTHOR_SUMMARY.AUTHOR_ID.ASC())
      .mapTo(AUTHOR_SUMMARY)
    val cards = BookCardTable(
      bookCardView(
        viewName = "book_card",
        query = SELECT FROM BOOK
      )
    )
    val rejected = listOf(
      SELECT
        .COLUMN(AUTHOR_SUMMARY.NAME)
        .FROM(AUTHOR_SUMMARY)
        .mapTo(AUTHOR_SUMMARY) to """AuthorSummary.id is required; select "author_summary"."author.id"""",
      SELECT
        .COLUMN(cards.TITLE)
        .FROM(cards)
        .mapTo(cards) to "mapTo(book_card) cannot read a view whose required properties hold tables or views"
    )

    assertThat(rows.compile().sql).isEqualTo(
      """SELECT "book"."title", "author_summary"."author.id", "author_summary"."name" FROM "main"."book" """ +
          """INNER JOIN "main"."author_summary" ON "book"."author" = "author_summary"."author.id" """ +
          """ORDER BY "book"."id" ASC"""
    )
    assertThat(rows.observedTables()).isEqualTo(listOf("book", "author"))
    assertThat(
      rejected.map { (query, _) ->
        assertThrows(IllegalArgumentException::class.java, query::compile).message
      }
    ).isEqualTo(rejected.map(Pair<*, String>::second))
    libraryDatabase().use { database ->
      database.createViews(AUTHOR_SUMMARY_VIEW)
      val connection = database.dbConnection()
      assertThat(
        rows
          .usingConnection(connection)
          .execute()
          .map { row -> row[BOOK.TITLE] to row[AUTHOR_SUMMARY.NAME] }
      ).isEqualTo(listOf("Dune" to "Ada", "Emma" to "Grace"))
      assertThat(summaries.usingConnection(connection).execute()).isEqualTo(
        listOf(
          AuthorSummary(
            id = 1L,
            name = "Ada"
          ),
          AuthorSummary(
            id = 2L,
            name = "Grace"
          )
        )
      )
    }
  }

  @Test
  fun `a view-typed property reads the nested view's columns after that view is created`() {
    val cards = SELECT FROM SUMMARY_CARD

    assertThat(SUMMARY_CARD_VIEW.definition.sql).isEqualTo(
      """SELECT "author_summary"."author.id" AS "author_summary.author.id", """ +
          """"author_summary"."name" AS "author_summary.name" FROM "main"."author_summary""""
    )
    assertThat(cards.compile().sql).isEqualTo(
      """SELECT "summary_card"."author_summary.author.id", "summary_card"."author_summary.name" """ +
          """FROM "main"."summary_card""""
    )
    assertThat(cards.observedTables()).isEqualTo(listOf("author"))
    libraryDatabase().use { database ->
      database.createViews(SUMMARY_CARD_VIEW, AUTHOR_SUMMARY_VIEW)
      assertThat(cards.usingConnection(database.dbConnection()).execute()).isEqualTo(
        listOf(
          SummaryCard(
            AuthorSummary(
              id = 1L,
              name = "Ada"
            )
          ),
          SummaryCard(
            AuthorSummary(
              id = 2L,
              name = "Grace"
            )
          )
        )
      )
    }
  }

  @Test
  fun `parameters and connections reject when views are created`() {
    val name = AUTHOR.NAME.asParameter("name")
    val parameterView = GeneratedView(
      viewName = "named_author",
      temporary = false,
      query = { SELECT COLUMN AUTHOR.ID FROM AUTHOR WHERE (AUTHOR.NAME IS name) },
      columns = listOf("author.id")
    )

    libraryDatabase().use { database ->
      val connectionView = GeneratedView(
        viewName = "connected_author",
        temporary = false,
        query = { (SELECT COLUMN AUTHOR.ID FROM AUTHOR).usingConnection(database.dbConnection()) },
        columns = listOf("author.id")
      )
      val errors = listOf(parameterView, connectionView).map { view ->
        assertThrows(IllegalArgumentException::class.java) {
          database.createViews(AUTHOR_SUMMARY_VIEW, view)
        }.message
      }
      assertThat(errors).isEqualTo(
        listOf(
          "Cannot create view 'named_author': its definition cannot take parameter 'name'",
          "Cannot create view 'connected_author': a view definition cannot use a connection"
        )
      )
      assertThat(database.viewNames()).isEmpty()
    }
  }

  @Test
  fun `computed outputs take aliases while definitions name unaliased fields`() {
    val query = SELECT
      .COLUMNS(BOOK.TITLE, (BOOK.ID + 1L) AS "next_id")
      .FROM(BOOK)
    val view = GeneratedView(
      viewName = "next_book",
      temporary = false,
      query = { query },
      columns = listOf("book.title", "next_id")
    )

    assertThat(listOf(query.compile().sql, view.definition.sql)).isEqualTo(
      listOf(
        """SELECT "book"."title", "book"."id" + ? AS "next_id" FROM "main"."book"""",
        """SELECT "book"."title" AS "book.title", "book"."id" + 1 AS "next_id" FROM "main"."book""""
      )
    )
    libraryDatabase().use { database ->
      database.createViews(view)
      assertThat(
        SELECT
          .RAW("""SELECT "book.title", next_id FROM next_book ORDER BY next_id""")
          .readAs(STRING, LONG)
          .usingConnection(database.dbConnection())
          .execute()
      ).isEqualTo(listOf(Row2("Dune", 2L), Row2("Emma", 3L), Row2("Kernel", 4L)))
    }
  }
}
