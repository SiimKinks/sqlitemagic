package com.siimkinks.sqlitemagic.runtime.contract.fulltext

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.BuildConfig
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.LetterFtsTable.Companion.LETTER_FTS
import com.siimkinks.sqlitemagic.LetterTable.Companion.LETTER
import com.siimkinks.sqlitemagic.MATCHINFO
import com.siimkinks.sqlitemagic.OFFSETS
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SNIPPET
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.fulltext.Letter
import com.siimkinks.sqlitemagic.fixture.model.fulltext.LetterAuthor
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.RxJava3Adapter
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withDefaultWriter
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test

internal class Fts4SearchTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  private val frank = LetterAuthor(
    id = 1,
    name = "Frank"
  )
  private val dune = Letter(
    id = 1,
    subject = "Dune",
    body = "Running across the desert planet",
    tag = "melange",
    language = 0,
    author = frank
  )
  private val sands = Letter(
    id = 2,
    subject = "Sands",
    body = null,
    tag = "dune",
    language = 0,
    author = frank
  )
  private val duna = Letter(
    id = 3,
    subject = "Duna",
    body = "La dune du désert",
    tag = "sable",
    language = 1,
    author = LetterAuthor(
      id = 2,
      name = "Anne"
    )
  )

  @Test
  fun searchesReadEntitiesWithTheirRelationshipsAndMatchInfo() {
    seed()

    val hits = SELECT
      .COLUMNS(LETTER, SNIPPET(LETTER_FTS.BODY, "[", "]", "…", 4), OFFSETS(LETTER_FTS))
      .FROM(LETTER)
      .WHERE(LETTER_FTS MATCH "run* OR sands")
      .ORDER_BY(LETTER.ID.ASC())
      .execute()
    val matchInfo = SELECT
      .COLUMN(MATCHINFO(LETTER_FTS))
      .FROM(LETTER)
      .WHERE(LETTER_FTS MATCH "dune")
      .execute()
      .map(ByteArray::size)

    assertThat(hits to matchInfo).isEqualTo(
      listOf(
        Row3(dune, "[Running] across the desert…", "1 0 0 7"),
        Row3(sands, null, "0 1 0 5")
      ) to listOf(44)
    )
  }

  @Test
  fun searchesOfALanguageAndOfFreeText() {
    seed()

    val searches = mapOf(
      "language 0" to (LETTER_FTS MATCH "dune"),
      "language 1" to ((LETTER_FTS MATCH "dune") AND (LETTER_FTS.LANGUAGE IS 1)),
      "unindexed column" to (LETTER_FTS MATCH "melange"),
      "stemmed prefix of free text" to (LETTER_FTS MATCH LETTER_FTS.terms("deserts plan", prefix = true)),
      "syntax in free text" to (LETTER_FTS MATCH LETTER_FTS.terms("""dune OR "sands" -""")),
      "quoted free text" to (LETTER_FTS MATCH LETTER_FTS.terms(""""Sands"""")),
      "column filter" to (LETTER_FTS.SUBJECT MATCH "desert")
    )

    assertThat(
      searches.mapValues { (_, search) ->
        SELECT
          .COLUMN(LETTER.ID)
          .FROM(LETTER)
          .WHERE(search)
          .ORDER_BY(LETTER.ID.ASC())
          .execute()
      }
    ).isEqualTo(
      mapOf(
        "language 0" to listOf(1L),
        "language 1" to listOf(3L),
        "unindexed column" to emptyList(),
        "stemmed prefix of free text" to listOf(1L),
        "syntax in free text" to emptyList(),
        "quoted free text" to listOf(2L),
        "column filter" to emptyList()
      )
    )
  }

  @Test
  fun entityWritesKeepSearchesAndTheIndexInStep() {
    seed()
    dune
      .copy(
        subject = "Arrakis",
        tag = "spice"
      )
      .update()
      .execute()
    duna
      .copy(language = 0)
      .update()
      .execute()
    sands
      .delete()
      .execute()

    val searches = mapOf(
      "old subject" to (LETTER_FTS.SUBJECT MATCH "dune"),
      "new subject" to (LETTER_FTS MATCH "arrakis"),
      "moved language" to (LETTER_FTS MATCH "du"),
      "deleted row" to (LETTER_FTS MATCH "sands")
    )

    assertThat(
      searches.mapValues { (_, search) ->
        SELECT
          .FROM(LETTER)
          .WHERE(search)
          .count()
          .execute()
      }
    ).isEqualTo(
      mapOf(
        "old subject" to 0L,
        "new subject" to 1L,
        "moved language" to 1L,
        "deleted row" to 0L
      )
    )
    assertThat(
      SELECT
        .COLUMNS(LETTER_FTS.ROWID, LETTER_FTS.TAG)
        .FROM(LETTER_FTS)
        .WHERE(LETTER_FTS MATCH "arrakis")
        .execute()
    ).isEqualTo(listOf(Row2(1L, "spice")))
    assertThat(
      listOf(LETTER_FTS.integrityCheck(), LETTER_FTS.rebuild(), LETTER_FTS.optimize()).map { it.execute() }
    ).isEqualTo(listOf(1, 1, 1))
  }

  @Test
  fun searchesRefreshWhenTheirTableChanges() {
    seed()
    val subjects = RxJava3Adapter
      .runQuery(
        SELECT
          .COLUMN(LETTER.SUBJECT)
          .FROM(LETTER)
          .WHERE(LETTER_FTS MATCH "dune")
          .ORDER_BY(LETTER.ID.ASC())
      )
      .record()
    try {
      Letter(
        id = 4,
        subject = "Dune Messiah",
        body = "The sequel",
        tag = "sand",
        language = 0,
        author = frank
      )
        .persist()
        .execute()

      subjects
        .assertNoErrors()
        .assertValuesOnly(listOf("Dune"), listOf("Dune", "Dune Messiah"))
    } finally {
      subjects.dispose()
    }
  }

  @Test
  fun anEntitySearchScansTheIndexFirstAndLooksUpItsRowsAndRelationshipsByKey() {
    seed()
    val sql = SELECT
      .FROM(LETTER)
      .WHERE(LETTER_FTS MATCH "dune")
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
      when (BuildConfig.SQLITE_ENGINE) {
        "bundled" -> listOf(
          "SCAN main.letter_fts VIRTUAL TABLE INDEX 5:",
          "SEARCH main.letter USING INTEGER PRIMARY KEY (rowid=?)",
          "SEARCH main.letter_author USING INTEGER PRIMARY KEY (rowid=?) LEFT-JOIN"
        )
        else -> listOf(
          "SCAN TABLE letter_fts VIRTUAL TABLE INDEX 5:",
          "SEARCH TABLE letter USING INTEGER PRIMARY KEY (rowid=?)",
          "SEARCH TABLE letter_author USING INTEGER PRIMARY KEY (rowid=?)"
        )
      }
    )
  }

  private fun seed() = listOf(dune, sands, duna).forEach { letter ->
    letter
      .persist()
      .execute()
  }
}
