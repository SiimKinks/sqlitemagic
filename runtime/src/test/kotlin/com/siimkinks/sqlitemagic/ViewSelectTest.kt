@file:Suppress("TestFunctionName")

package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlUtil.createViews
import com.siimkinks.sqlitemagic.internal.ViewBlock
import com.siimkinks.sqlitemagic.internal.getRequiredText
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.migration.MigrationExecutor
import com.siimkinks.sqlitemagic.migration.MigrationPlan
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
  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = SeriesEnding(
    name = statement.getRequiredText(
      index = startIndex,
      nullMessage = """Column "name" was NULL"""
    ),
    lastBook = checkNotNull(related).readOrNull(
      statement = statement,
      relationship = 0
    ) as Book?
  )

  override infix fun AS(alias: String) = SeriesEndingTable(alias)
}

private val SERIES_ENDING_VIEW = GeneratedView(
  viewName = "series_ending",
  temporary = false,
  query = {
    val lastBook = BOOK AS "last_book"
    SELECT
      .COLUMNS(SERIES.NAME AS "name", lastBook.nullable())
      .FROM(SERIES)
      .LEFT_JOIN(lastBook)
      .ON(SERIES.LAST_BOOK IS lastBook.ID)
  },
  columns = listOf("name"),
  blocks = listOf(
    ViewBlock(
      key = "last_book",
      target = BOOK,
      path = "SeriesEnding.lastBook",
      nullable = true
    )
  )
)

private val SERIES_ENDING = SeriesEndingTable()

private class SummaryCardTable(alias: String? = null) : Table<SummaryCard>(
  name = "summary_card",
  alias = alias,
  generatedView = SUMMARY_CARD_VIEW
) {
  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = SummaryCard(
    summary = checkNotNull(related).read(
      statement = statement,
      relationship = 0
    ) as AuthorSummary
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
      target = AUTHOR_SUMMARY,
      path = "SummaryCard.summary",
      nullable = false
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
  @Suppress("PropertyName")
  val TITLE = Column(
    table = this,
    name = "title",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding(),
    selectedRead = { statement, index ->
      statement.getRequiredText(
        index = index,
        nullMessage = BOOK_TITLE_NULL
      )
    }
  )

  override val entityFields = listOf(TITLE)

  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = BookCard(
    title = statement.getRequiredText(
      index = startIndex,
      nullMessage = BOOK_TITLE_NULL
    ),
    book = checkNotNull(related).read(
      statement = statement,
      relationship = 0
    ) as Book
  )

  override infix fun AS(alias: String) = BookCardTable(
    view = view,
    alias = alias
  )
}

private const val BOOK_TITLE_NULL = """Column "title" was NULL"""

private fun bookCardView(
  viewName: String,
  query: SelectQuery<*, *>,
  key: String = "book"
) = GeneratedView(
  viewName = viewName,
  temporary = false,
  query = { query },
  columns = listOf("title"),
  blocks = listOf(
    ViewBlock(
      key = key,
      target = BOOK,
      path = "BookCard.book",
      nullable = false
    )
  )
)

private fun cardsOfBooks() = SELECT
  .COLUMNS(BOOK.TITLE AS "title", BOOK)
  .FROM(BOOK)

private data class BookEntry(val book: Book)

private class BookEntryTable(
  private val view: GeneratedView,
  alias: String? = null
) : Table<BookEntry>(
  name = view.viewName,
  alias = alias,
  generatedView = view
) {
  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = BookEntry(
    checkNotNull(related).read(
      statement = statement,
      relationship = 0
    ) as Book
  )

  override infix fun AS(alias: String) = BookEntryTable(
    view = view,
    alias = alias
  )
}

internal class ViewSelectTest {
  private fun TestDatabase.viewNames() = SELECT
    .RAW("SELECT name FROM sqlite_master WHERE type = 'view'")
    .readAs(STRING)
    .usingConnection(connection)
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
      val connection = database.connection
      assertThat(
        summaries
          .usingConnection(connection)
          .execute()
      ).isEqualTo(
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
  fun `table blocks read the relationships their entity value plans`() {
    val shallowView = bookCardView(
      viewName = "book_card",
      query = cardsOfBooks()
    )
    val deepView = bookCardView(
      viewName = "deep_book_card",
      query = cardsOfBooks().queryDeep()
    )
    val shallow = SELECT FROM BookCardTable(shallowView)
    val deep = SELECT FROM BookCardTable(deepView)

    assertThat(listOf(shallowView, deepView).map { it.definition.sql }).isEqualTo(
      listOf(
        """SELECT "book"."title" AS "title", "book"."id" AS "book.id", "book"."title" AS "book.title", """ +
            """"book"."author" AS "book.author", "author"."id" AS "book.author.id", """ +
            """"author"."name" AS "book.author.name", "author"."publisher" AS "book.author.publisher" """ +
            """FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = "author"."id"""",
        """SELECT "book"."title" AS "title", "book"."id" AS "book.id", "book"."title" AS "book.title", """ +
            """"book"."author" AS "book.author", "author"."id" AS "book.author.id", """ +
            """"author"."name" AS "book.author.name", "author"."publisher" AS "book.author.publisher", """ +
            """"publisher"."id" AS "book.author.publisher.id", "publisher"."name" AS "book.author.publisher.name" """ +
            """FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = "author"."id" """ +
            """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id""""
      )
    )
    assertThat(shallow.compile().sql).isEqualTo(
      """SELECT "book_card"."title", "book_card"."book.id", "book_card"."book.title", "book_card"."book.author", """ +
          """"book_card"."book.author.id", "book_card"."book.author.name", "book_card"."book.author.publisher" """ +
          """FROM "main"."book_card""""
    )
    assertThat(deep.compile().sql).isEqualTo(
      """SELECT "deep_book_card"."title", "deep_book_card"."book.id", "deep_book_card"."book.title", """ +
          """"deep_book_card"."book.author", "deep_book_card"."book.author.id", """ +
          """"deep_book_card"."book.author.name", "deep_book_card"."book.author.publisher", """ +
          """"deep_book_card"."book.author.publisher.id", "deep_book_card"."book.author.publisher.name" """ +
          """FROM "main"."deep_book_card""""
    )
    libraryDatabase().use { database ->
      database.createViews(shallowView, deepView)
      val connection = database.connection
      assertThat(
        shallow
          .usingConnection(connection)
          .execute()
      ).isEqualTo(bookCards(shallow = true))
      assertThat(
        deep
          .usingConnection(connection)
          .execute()
      ).isEqualTo(bookCards(shallow = false))
    }
  }

  @Test
  fun `table blocks read relationships from the earlier occurrences their joins match`() {
    val view = bookCardView(
      viewName = "book_card",
      query = SELECT
        .COLUMNS(BOOK.TITLE AS "title", BOOK)
        .FROM(PUBLISHER)
        .INNER_JOIN(AUTHOR)
        .ON(AUTHOR.PUBLISHER IS PUBLISHER.ID)
        .INNER_JOIN(BOOK)
        .ON(BOOK.AUTHOR IS AUTHOR.ID)
        .queryDeep()
    )
    val card = BookCardTable(view)
    val cards = libraryDatabase().use { database ->
      database.createViews(view)
      (SELECT FROM card ORDER_BY card.TITLE.ASC()).rows(database)
    }

    assertThat(cards).isEqualTo(listOf(0, 2).map(bookCards(shallow = false)::get))
  }

  @Test
  fun `a view entity value after other values reads its blocks from its own columns`() {
    val view = bookCardView(
      viewName = "deep_book_card",
      query = cardsOfBooks().queryDeep()
    )
    val card = BookCardTable(view)
    val query = SELECT
      .COLUMNS(card.TITLE, card)
      .FROM(card)

    val rows = libraryDatabase().use { database ->
      database.createViews(view)
      query.rows(database)
    }

    assertThat(query.compile().sql to rows).isEqualTo(
      """SELECT "deep_book_card"."title", "deep_book_card"."title", "deep_book_card"."book.id", """ +
          """"deep_book_card"."book.title", "deep_book_card"."book.author", "deep_book_card"."book.author.id", """ +
          """"deep_book_card"."book.author.name", "deep_book_card"."book.author.publisher", """ +
          """"deep_book_card"."book.author.publisher.id", "deep_book_card"."book.author.publisher.name" """ +
          """FROM "main"."deep_book_card"""" to
          bookCards(shallow = false).map { Row2(it.title, it) }
    )
  }

  @Test
  fun `table blocks whose related row is missing fall back to the stored ID or fail`() {
    val shallowView = bookCardView(
      viewName = "book_card",
      query = cardsOfBooks()
    )
    val deepView = bookCardView(
      viewName = "deep_book_card",
      query = cardsOfBooks().queryDeep()
    )
    libraryDatabase().use { database ->
      database.createViews(shallowView, deepView)
      database.execSQL("DELETE FROM publisher WHERE id = 1")
      assertThat(
        (SELECT FROM BookCardTable(deepView))
          .rows(database)
          .first()
      )
        .isEqualTo(
          BookCard(
            title = "Dune",
            book = books(shallow = false)
              .first()
              .run { copy(author = author.copy(publisher = Publisher(id = 1L))) }
          )
        )
      database.execSQL("DELETE FROM author WHERE id = 1")
      assertThat(sqlExceptionMessage { (SELECT FROM BookCardTable(shallowView)).rows(database) })
        .isEqualTo("""Required relationship "author" referenced a missing row""")
    }
  }

  @Test
  fun `a block reads the entity value of its key's occurrence`() {
    val entry = BOOK AS "entry"
    val view = bookCardView(
      viewName = "entry_card",
      query = SELECT
        .COLUMNS(entry.TITLE AS "title", entry)
        .FROM(entry),
      key = "entry"
    )

    val cards = libraryDatabase().use { database ->
      database.createViews(view)
      (SELECT FROM BookCardTable(view)).rows(database)
    }

    assertThat(view.definition.sql to cards).isEqualTo(
      """SELECT "entry"."title" AS "title", "entry"."id" AS "entry.id", "entry"."title" AS "entry.title", """ +
          """"entry"."author" AS "entry.author", "author"."id" AS "entry.author.id", """ +
          """"author"."name" AS "entry.author.name", "author"."publisher" AS "entry.author.publisher" """ +
          """FROM "main"."book" AS "entry" LEFT JOIN "main"."author" ON "entry"."author" = "author"."id"""" to
          bookCards(shallow = true)
    )
  }

  @Test
  fun `SELECT FROM a subquery or CTE keeps its entity's key and column names`() {
    val derived = cardsOfBooks() AS "d"
    val shared = cardsOfBooks() AS "c"
    val cases = listOf(
      "derived source" to bookCardView(
        viewName = "derived_card",
        query = SELECT FROM derived
      ),
      "common table expression" to bookCardView(
        viewName = "cte_card",
        query = WITH(shared) { c -> SELECT FROM c }
      ),
      "derived entity" to bookCardView(
        viewName = "derived_entity_card",
        query = SELECT
          .COLUMNS(derived[BOOK.TITLE] AS "title", derived[BOOK])
          .FROM(derived),
        key = "d.book"
      )
    )

    val reads = libraryDatabase().use { database ->
      database.createViews(
        *cases
          .map(Pair<String, GeneratedView>::second)
          .toTypedArray()
      )
      cases.map { (label, view) -> label to (SELECT FROM BookCardTable(view)).rows(database) }
    }

    assertThat(reads).isEqualTo(cases.map { (label, _) -> label to bookCards(shallow = true) })
  }

  @Test
  fun `definitions name outputs by the names their selection exports`() {
    val writer = AUTHOR AS "writer"
    val authorBook = BOOK AS "author"
    val views = listOf(
      GeneratedView(
        viewName = "writer_name",
        temporary = false,
        query = {
          SELECT
            .COLUMNS(writer, AUTHOR.NAME)
            .FROM(BOOK)
            .INNER_JOIN(writer)
            .ON(BOOK.AUTHOR IS writer.ID)
        },
        columns = listOf("author.name")
      ),
      GeneratedView(
        viewName = "joined_author",
        temporary = false,
        query = {
          SELECT
            .COLUMNS(authorBook.ID, AUTHOR.NAME)
            .FROM(authorBook)
        },
        columns = listOf("author.id", "author.name")
      ),
      GeneratedView(
        viewName = "next_title",
        temporary = false,
        query = {
          SELECT
            .COLUMNS(BOOK.TITLE, BOOK.ID + 1L)
            .FROM(BOOK)
        },
        columns = listOf("book.title", "_2")
      )
    )

    val columns = libraryDatabase().use { database ->
      database.createViews(*views.toTypedArray())
      views.map { view ->
        SELECT
          .RAW("SELECT name FROM pragma_table_info('${view.viewName}')")
          .readAs(STRING)
          .usingConnection(database.connection)
          .execute()
      }
    }

    assertThat(views.map { it.definition.sql } to columns).isEqualTo(
      listOf(
        """SELECT "writer"."id" AS "writer.id", "writer"."name" AS "writer.name", """ +
            """"writer"."publisher" AS "writer.publisher", "writer"."name" AS "author.name" FROM "main"."book" """ +
            """INNER JOIN "main"."author" AS "writer" ON "book"."author" = "writer"."id"""",
        """SELECT "author"."id" AS "author.id", "author_2"."name" AS "author.name" FROM "main"."book" AS "author" """ +
            """LEFT JOIN "main"."author" AS "author_2" ON "author"."author" = "author_2"."id"""",
        """SELECT "book"."title" AS "book.title", "book"."id" + 1 AS "_2" FROM "main"."book""""
      ) to listOf(
        listOf("writer.id", "writer.name", "writer.publisher", "author.name"),
        listOf("author.id", "author.name"),
        listOf("book.title", "_2")
      )
    )
  }

  @Test
  fun `a one-block view selects its entity with SELECT FROM`() {
    val view = GeneratedView(
      viewName = "book_entry",
      temporary = false,
      query = { (SELECT FROM BOOK).queryDeep() },
      columns = emptyList(),
      blocks = listOf(
        ViewBlock(
          key = "book",
          target = BOOK,
          path = "BookEntry.book",
          nullable = false
        )
      )
    )

    val entries = libraryDatabase().use { database ->
      database.createViews(view)
      (SELECT FROM BookEntryTable(view)).rows(database)
    }

    assertThat(entries).isEqualTo(books(shallow = false).map(::BookEntry))
  }

  @Test
  fun `all() columns fill scalar properties beside an entity value`() {
    val view = GeneratedView(
      viewName = "author_named_card",
      temporary = false,
      query = {
        SELECT
          .COLUMNS(AUTHOR.all(), BOOK)
          .FROM(BOOK)
      },
      columns = listOf("author.name"),
      blocks = listOf(
        ViewBlock(
          key = "book",
          target = BOOK,
          path = "BookCard.book",
          nullable = false
        )
      )
    )

    val cards = libraryDatabase().use { database ->
      database.createViews(view)
      (SELECT FROM BookCardTable(view)).rows(database)
    }

    assertThat(view.definition.sql to cards).isEqualTo(
      """SELECT "author"."id" AS "author.id", "author"."name" AS "author.name", """ +
          """"author"."publisher" AS "author.publisher", "book"."id" AS "book.id", "book"."title" AS "book.title", """ +
          """"book"."author" AS "book.author", "author"."id" AS "book.author.id", """ +
          """"author"."name" AS "book.author.name", "author"."publisher" AS "book.author.publisher" """ +
          """FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = "author"."id"""" to
          books(shallow = true).map { book ->
            BookCard(
              title = book.author.name,
              book = book
            )
          }
    )
  }

  @Test
  fun `a ported definition replaces an installed 3_0 view only when the views are recreated`() {
    val view = bookCardView(
      viewName = "book_card",
      query = cardsOfBooks()
    )
    val cards = SELECT FROM BookCardTable(view)

    val reads = libraryDatabase().use { database ->
      database.execSQL(
        """CREATE VIEW "book_card" AS SELECT "book"."title" AS "title", "book"."id" AS "book.id", """ +
            """"book"."title" AS "book.title", "book"."author" AS "book.author", "author"."id" AS "author.id", """ +
            """"author"."name" AS "author.name", "author"."publisher" AS "author.publisher" FROM "main"."book" """ +
            """LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""
      )
      database.createViews(view)
      val kept = sqliteErrorMessage { cards.rows(database) }
      database.connection.write { lease ->
        MigrationExecutor(
          resources = { error("A migration without scripts opens no resources") },
          connection = lease.connection
        ).execute(
          plan = MigrationPlan(
            fromVersion = 1,
            toVersion = 2,
            steps = emptyList()
          ),
          createTargetViews = {
            createViews(
              connection = lease.connection,
              views = listOf(view),
              temporary = false
            )
          }
        )
      }
      kept to cards.rows(database)
    }

    assertThat(reads).isEqualTo("no such column: book_card.book.author.id" to bookCards(shallow = true))
  }

  @Test
  fun `view definitions reject blocks without their entity values before DDL`() {
    val b = BOOK AS "b"
    val writer = AUTHOR AS "book"
    val authorBook = BOOK AS "author"
    val cases = listOf(
      Triple(
        "plain columns only",
        bookCardView(
          viewName = "book_card",
          query = SELECT
            .COLUMNS(BOOK.TITLE AS "title", BOOK.ID)
            .FROM(BOOK)
        ),
        "Cannot create view 'book_card': BookCard.book reads an entity; select the book entity"
      ),
      Triple(
        "all() of the occurrence",
        bookCardView(
          viewName = "book_card",
          query = SELECT
            .COLUMNS(BOOK.TITLE AS "title", BOOK.all())
            .FROM(BOOK)
        ),
        "Cannot create view 'book_card': BookCard.book reads an entity, and its definition selects only the " +
            "columns of book; select the book entity instead of its all()"
      ),
      Triple(
        "an entity of another table",
        bookCardView(
          viewName = "book_card",
          query = SELECT
            .COLUMNS(b.TITLE AS "title", writer)
            .FROM(b)
            .INNER_JOIN(writer)
            .ON(b.AUTHOR IS writer.ID)
        ),
        "Cannot create view 'book_card': BookCard.book reads the book entity, and its definition selects the " +
            "author entity as book"
      ),
      Triple(
        "a nullable entity for a required property",
        bookCardView(
          viewName = "book_card",
          query = SELECT
            .COLUMNS(AUTHOR.NAME AS "title", BOOK.nullable())
            .FROM(AUTHOR)
            .LEFT_JOIN(BOOK)
            .ON(BOOK.AUTHOR IS AUTHOR.ID)
        ),
        "Cannot create view 'book_card': BookCard.book is not nullable, and its definition selects the book " +
            "entity as nullable"
      ),
      Triple(
        "an outer-joined entity without nullable()",
        bookCardView(
          viewName = "book_card",
          query = SELECT
            .COLUMNS(AUTHOR.NAME AS "title", BOOK)
            .FROM(AUTHOR)
            .LEFT_JOIN(BOOK)
            .ON(BOOK.AUTHOR IS AUTHOR.ID)
        ),
        "An outer join can leave the book entity NULL; select book.nullable()"
      ),
      Triple(
        "a scalar column inside the entity",
        GeneratedView(
          viewName = "book_card",
          temporary = false,
          query = { SELECT FROM BOOK },
          columns = listOf("book.title"),
          blocks = listOf(
            ViewBlock(
              key = "book",
              target = BOOK,
              path = "BookCard.book",
              nullable = false
            )
          )
        ),
        "Cannot create view 'book_card': column book.title is part of the selected book entity; a scalar property " +
            "reads a column selected itself, so select it with an alias"
      ),
      Triple(
        "the occurrence-qualified key of SELECT FROM a subquery",
        bookCardView(
          viewName = "book_card",
          query = SELECT FROM (cardsOfBooks() AS "d"),
          key = "d.book"
        ),
        "Cannot create view 'book_card': BookCard.book reads an entity; select the book entity of occurrence d.book"
      ),
      Triple(
        "a default handle named like a field of an aliased occurrence",
        GeneratedView(
          viewName = "book_card",
          temporary = false,
          query = {
            SELECT
              .COLUMNS(authorBook.ID, AUTHOR.ID)
              .FROM(authorBook)
          },
          columns = listOf("author.id")
        ),
        """Cannot create view 'book_card': its definition exports two columns named author.id, from selected """ +
            """value 1 ("author"."id") and selected value 2 ("author"."id"); alias one of them"""
      ),
      Triple(
        "a plain column named like an entity column",
        bookCardView(
          viewName = "book_card",
          query = SELECT
            .COLUMNS(BOOK.TITLE AS "title", BOOK, BOOK.TITLE)
            .FROM(BOOK)
        ),
        """Cannot create view 'book_card': its definition exports two columns named book.title, from selected """ +
            """value 2 (the book entity) and selected value 3 ("book"."title"); alias one of them"""
      )
    )

    val errors = libraryDatabase().use { database ->
      cases.map { (label, view, _) ->
        val error = assertThrows(IllegalArgumentException::class.java) {
          database.createViews(AUTHOR_SUMMARY_VIEW, view)
        }
        label to (error.message to database.viewNames())
      }
    }

    assertThat(errors).isEqualTo(cases.map { (label, _, message) -> label to (message to emptyList<String>()) })
  }

  @Test
  fun `a nullable block reads null when all its columns are NULL`() {
    libraryDatabase().use { database ->
      database.createViews(SERIES_ENDING_VIEW)
      assertThat((SELECT FROM SERIES_ENDING).rows(database)).isEqualTo(
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
        query = cardsOfBooks()
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
      val connection = database.connection
      assertThat(
        rows
          .usingConnection(connection)
          .execute()
          .map { row -> row[BOOK.TITLE] to row[AUTHOR_SUMMARY.NAME] }
      ).isEqualTo(listOf("Dune" to "Ada", "Emma" to "Grace"))
      assertThat(
        summaries
          .usingConnection(connection)
          .execute()
      ).isEqualTo(
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
      assertThat(cards.rows(database)).isEqualTo(
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
        query = { (SELECT COLUMN AUTHOR.ID FROM AUTHOR).usingConnection(database.connection) },
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
          .usingConnection(database.connection)
          .execute()
      ).isEqualTo(listOf(Row2("Dune", 2L), Row2("Emma", 3L), Row2("Kernel", 4L)))
    }
  }
}
