package com.siimkinks.sqlitemagic.runtime.contract.fulltext

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SearchBookFtsTable.Companion.SEARCH_BOOK_FTS
import com.siimkinks.sqlitemagic.SearchBookTable.Companion.SEARCH_BOOK
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.fulltext.SearchAuthor
import com.siimkinks.sqlitemagic.fixture.model.fulltext.SearchBook
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.RxJava3Adapter
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withDefaultWriter
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test

internal class FullTextSearchTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  private val frank = SearchAuthor(
    id = 1,
    name = "Frank"
  )
  private val dune = SearchBook(
    id = 1,
    title = "Dune",
    summary = "A desert planet and its dune seas",
    author = frank
  )
  private val sands = SearchBook(
    id = 2,
    title = "Sands",
    summary = "Crossing the desert",
    author = frank
  )
  private val emma = SearchBook(
    id = 3,
    title = "Emma",
    summary = "A novel of manners",
    author = SearchAuthor(
      id = 2,
      name = "Jane"
    )
  )

  @Test
  fun searchesReadEntitiesWithTheirRelationshipsBestMatchFirst() {
    seed()

    assertThat(
      SELECT
        .FROM(SEARCH_BOOK)
        .WHERE(SEARCH_BOOK_FTS MATCH "dune OR desert")
        .ORDER_BY(SEARCH_BOOK_FTS.RANK.ASC())
        .execute()
    ).isEqualTo(listOf(dune, sands))
  }

  @Test
  fun entityWritesKeepSearchesAndCountsInStep() {
    seed()
    val arrakis = dune.copy(title = "Arrakis")
    arrakis
      .update()
      .execute()
    sands
      .delete()
      .execute()

    val searches = mapOf(
      "old title" to (SEARCH_BOOK_FTS.TITLE MATCH "dune"),
      "new title" to (SEARCH_BOOK_FTS MATCH "arrakis"),
      "unchanged summary" to (SEARCH_BOOK_FTS MATCH "dune"),
      "deleted row" to (SEARCH_BOOK_FTS MATCH "crossing")
    )

    assertThat(
      searches.mapValues { (_, search) ->
        SELECT
          .FROM(SEARCH_BOOK)
          .WHERE(search)
          .count()
          .execute()
      }
    ).isEqualTo(
      mapOf(
        "old title" to 0L,
        "new title" to 1L,
        "unchanged summary" to 1L,
        "deleted row" to 0L
      )
    )
    assertThat(
      SELECT
        .COLUMNS(SEARCH_BOOK_FTS.ROWID, SEARCH_BOOK_FTS.TITLE)
        .FROM(SEARCH_BOOK_FTS)
        .WHERE(SEARCH_BOOK_FTS MATCH "desert")
        .execute()
    ).isEqualTo(listOf(Row2(1L, "Arrakis")))
  }

  @Test
  fun searchesRefreshWhenTheirTableChanges() {
    seed()
    val titles = RxJava3Adapter
      .runQuery(
        SELECT
          .COLUMN(SEARCH_BOOK.TITLE)
          .FROM(SEARCH_BOOK)
          .WHERE(SEARCH_BOOK_FTS MATCH "dune")
          .ORDER_BY(SEARCH_BOOK.ID.ASC())
      )
      .record()
    try {
      SearchBook(
        id = 4,
        title = "Dune Messiah",
        summary = "The sequel",
        author = frank
      )
        .persist()
        .execute()

      titles
        .assertNoErrors()
        .assertValuesOnly(listOf("Dune"), listOf("Dune", "Dune Messiah"))
    } finally {
      titles.dispose()
    }
  }

  @Test
  fun anEntitySearchScansTheIndexFirstAndLooksUpItsRowsAndRelationshipsByKey() {
    seed()
    val sql = SELECT
      .FROM(SEARCH_BOOK)
      .WHERE(SEARCH_BOOK_FTS MATCH "dune")
      .compile()
      .sql

    val plan = withDefaultWriter { connection ->
      connection.prepare("EXPLAIN QUERY PLAN $sql").use { statement ->
        statement.bindText(
          index = 1,
          value = "dune"
        )
        buildList {
          while (statement.step()) {
            add(statement.getText(3))
          }
        }
      }
    }

    assertThat(plan).isEqualTo(
      listOf(
        "SCAN main.search_book_fts VIRTUAL TABLE INDEX 0:M2",
        "SEARCH main.search_book USING INTEGER PRIMARY KEY (rowid=?)",
        "SEARCH main.search_author USING INTEGER PRIMARY KEY (rowid=?) LEFT-JOIN"
      )
    )
  }

  private fun seed() = listOf(dune, sands, emma).forEach { book ->
    book
      .persist()
      .execute()
  }
}
