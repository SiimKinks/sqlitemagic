package com.siimkinks.sqlitemagic.runtime.contract.fulltext

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SearchBookFtsTable.Companion.SEARCH_BOOK_FTS
import com.siimkinks.sqlitemagic.SearchBookTable.Companion.SEARCH_BOOK
import com.siimkinks.sqlitemagic.SearchNoteFtsTable.Companion.SEARCH_NOTE_FTS
import com.siimkinks.sqlitemagic.SearchNoteTable.Companion.SEARCH_NOTE
import com.siimkinks.sqlitemagic.SearchNotes
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.fulltext.SearchAuthor
import com.siimkinks.sqlitemagic.fixture.model.fulltext.SearchBook
import com.siimkinks.sqlitemagic.fixture.model.fulltext.SearchNote
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.withDefaultWriter
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test

internal class Fts5SearchTest {
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
  fun searchesOfATableKeyedByTextJoinAndCompareItsRowIds() {
    SearchNotes
      .insert(
        listOf(
          SearchNote(
            code = "b",
            text = "Buy desert dates"
          ),
          SearchNote(
            code = "a",
            text = "Cross the desert by night"
          ),
          SearchNote(
            code = "c",
            text = "Plant the seeds"
          )
        )
      )
      .execute()

    assertThat(
      mapOf(
        "automatic join" to SELECT
          .COLUMN(SEARCH_NOTE.CODE)
          .FROM(SEARCH_NOTE)
          .WHERE(SEARCH_NOTE_FTS MATCH "desert")
          .ORDER_BY(SEARCH_NOTE.ROW_ID.ASC())
          .execute(),
        "explicit join" to SELECT
          .COLUMN(SEARCH_NOTE.CODE)
          .FROM(SEARCH_NOTE)
          .INNER_JOIN(SEARCH_NOTE_FTS)
          .ON(SEARCH_NOTE_FTS.ROWID IS SEARCH_NOTE.ROW_ID)
          .WHERE(SEARCH_NOTE_FTS MATCH "desert")
          .ORDER_BY(SEARCH_NOTE.ROW_ID.ASC())
          .execute(),
        "row IDs from a subquery" to SELECT
          .COLUMN(SEARCH_NOTE.CODE)
          .FROM(SEARCH_NOTE)
          .WHERE(
            SEARCH_NOTE.ROW_ID IN SELECT
              .COLUMN(SEARCH_NOTE_FTS.ROWID)
              .FROM(SEARCH_NOTE_FTS)
              .WHERE(SEARCH_NOTE_FTS MATCH "seeds")
          )
          .execute(),
        "entities" to SELECT
          .FROM(SEARCH_NOTE)
          .WHERE(SEARCH_NOTE_FTS MATCH "night")
          .execute()
      )
    ).isEqualTo(
      mapOf(
        "automatic join" to listOf("b", "a"),
        "explicit join" to listOf("b", "a"),
        "row IDs from a subquery" to listOf("c"),
        "entities" to listOf(
          SearchNote(
            code = "a",
            rowId = 2,
            text = "Cross the desert by night"
          )
        )
      )
    )
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
