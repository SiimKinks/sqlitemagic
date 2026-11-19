package com.siimkinks.sqlitemagic.runtime.contract.fulltext

import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.LetterFtsTable.Companion.LETTER_FTS
import com.siimkinks.sqlitemagic.LetterTable.Companion.LETTER
import com.siimkinks.sqlitemagic.MATCHINFO
import com.siimkinks.sqlitemagic.MemoFtsTable.Companion.MEMO_FTS
import com.siimkinks.sqlitemagic.MemoTable.Companion.MEMO
import com.siimkinks.sqlitemagic.Memos
import com.siimkinks.sqlitemagic.OFFSETS
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SNIPPET
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.fulltext.Letter
import com.siimkinks.sqlitemagic.fixture.model.fulltext.LetterAuthor
import com.siimkinks.sqlitemagic.fixture.model.fulltext.Memo
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.RxJava3Adapter
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
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
      "stemmed prefix of free text" to (LETTER_FTS MATCH LETTER_FTS.terms(
        text = "deserts plan",
        prefix = true
      )),
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
    ).isEqualTo(listOf(1, 2, 1))
  }

  @Test
  fun updatesThatAConflictSkipsOrStopsLeaveTheIndexInStep() {
    Memos
      .insert(
        listOf(
          Memo(
            code = "a",
            title = "Trip",
            text = "Cross the desert"
          ),
          Memo(
            code = "b",
            title = "Groceries",
            text = "Buy dates"
          )
        )
      )
      .execute()
    fun renamed(conflictAlgorithm: Int) = UPDATE
      .OR(conflictAlgorithm)
      .TABLE(MEMO)
      .SET(MEMO.TITLE, "Groceries")
      .SET(MEMO.TEXT, "Sail the sea")
      .WHERE(MEMO.CODE IS "a")

    val results = listOf(
      Memo(
        code = "a",
        title = "Groceries",
        text = "Sail the sea"
      )
        .update()
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute(),
      renamed(CONFLICT_IGNORE).execute(),
      runCatching { renamed(CONFLICT_FAIL).execute() }.isFailure
    )

    assertThat(
      Triple(
        results,
        listOf("desert", "sea").map { query ->
          SELECT
            .COLUMN(MEMO_FTS.ROWID)
            .FROM(MEMO_FTS)
            .WHERE(MEMO_FTS MATCH query)
            .execute()
        },
        MEMO_FTS
          .integrityCheck()
          .execute()
      )
    ).isEqualTo(
      Triple(
        listOf(false, 0, true),
        listOf(listOf(1L), emptyList()),
        1
      )
    )
  }

  @Test
  fun replacingWritesKeepTheIndexInStep() {
    seed()
    Memos
      .insert(
        listOf(
          Memo(
            code = "a",
            title = "Trip",
            text = "Cross the desert"
          ),
          Memo(
            code = "b",
            title = "Groceries",
            text = "Buy dates"
          )
        )
      )
      .execute()

    val results = listOf(
      dune
        .copy(
          subject = "Arrakis",
          author = LetterAuthor(
            id = 3,
            name = "Paul"
          )
        )
        .insert()
        .conflictAlgorithm(CONFLICT_REPLACE)
        .execute(),
      Memo(
        code = "a",
        title = "Trip",
        text = "Sail the sea"
      )
        .insert()
        .conflictAlgorithm(CONFLICT_REPLACE)
        .execute(),
      Memo(
        code = "b",
        title = "Trip",
        text = "Buy figs"
      )
        .update()
        .conflictAlgorithm(CONFLICT_REPLACE)
        .execute()
    )
    val memoSearches = mapOf(
      "memo replaced by an insert" to "desert",
      "memo replaced by an update" to "sea",
      "updated memo" to "figs OR dates"
    )

    assertThat(
      listOf(
        results,
        SELECT
          .COLUMN(LETTER_FTS.ROWID)
          .FROM(LETTER_FTS)
          .WHERE(LETTER_FTS MATCH "dune OR arrakis")
          .execute(),
        memoSearches.mapValues { (_, query) ->
          SELECT
            .COLUMN(MEMO_FTS.ROWID)
            .FROM(MEMO_FTS)
            .WHERE(MEMO_FTS MATCH query)
            .execute()
        },
        listOf(LETTER_FTS.integrityCheck(), MEMO_FTS.integrityCheck()).map { it.execute() }
      )
    ).isEqualTo(
      listOf(
        listOf(
          EntityInsertResult.Inserted(1),
          EntityInsertResult.Inserted(3),
          true
        ),
        listOf(1L),
        mapOf(
          "memo replaced by an insert" to emptyList(),
          "memo replaced by an update" to emptyList(),
          "updated memo" to listOf(2L)
        ),
        listOf(1, 1)
      )
    )
  }

  @Test
  fun aCascadingDeleteRemovesTheChildrenFromTheIndex() {
    seed()
    frank
      .delete()
      .execute()

    val searches = mapOf(
      "letters of the deleted author" to (LETTER_FTS MATCH "running OR sands"),
      "letter of another author" to ((LETTER_FTS MATCH "dune") AND (LETTER_FTS.LANGUAGE IS 1))
    )

    assertThat(
      searches.mapValues { (_, search) ->
        SELECT
          .COLUMN(LETTER_FTS.ROWID)
          .FROM(LETTER_FTS)
          .WHERE(search)
          .execute()
      } to LETTER_FTS
        .integrityCheck()
        .execute()
    ).isEqualTo(
      mapOf(
        "letters of the deleted author" to emptyList(),
        "letter of another author" to listOf(3L)
      ) to 1
    )
  }

  @Test
  fun searchesOfATableKeyedByTextJoinAndCompareItsRowIds() {
    Memos
      .insert(
        listOf(
          Memo(
            code = "b",
            title = "Groceries",
            text = "Buy desert dates"
          ),
          Memo(
            code = "a",
            title = "Trip",
            text = "Cross the desert by night"
          ),
          Memo(
            code = "c",
            title = "Garden",
            text = "Plant the seeds"
          )
        )
      )
      .execute()

    assertThat(
      mapOf(
        "automatic join" to SELECT
          .COLUMN(MEMO.CODE)
          .FROM(MEMO)
          .WHERE(MEMO_FTS MATCH "desert")
          .ORDER_BY(MEMO.ROW_ID.ASC())
          .execute(),
        "explicit join" to SELECT
          .COLUMN(MEMO.CODE)
          .FROM(MEMO)
          .INNER_JOIN(MEMO_FTS)
          .ON(MEMO_FTS.ROWID IS MEMO.ROW_ID)
          .WHERE(MEMO_FTS MATCH "desert")
          .ORDER_BY(MEMO.ROW_ID.ASC())
          .execute(),
        "row IDs from a subquery" to SELECT
          .COLUMN(MEMO.CODE)
          .FROM(MEMO)
          .WHERE(
            MEMO.ROW_ID IN SELECT
              .COLUMN(MEMO_FTS.ROWID)
              .FROM(MEMO_FTS)
              .WHERE(MEMO_FTS MATCH "seeds")
          )
          .execute(),
        "entities" to SELECT
          .FROM(MEMO)
          .WHERE(MEMO_FTS MATCH "night")
          .execute()
      )
    ).isEqualTo(
      mapOf(
        "automatic join" to listOf("b", "a"),
        "explicit join" to listOf("b", "a"),
        "row IDs from a subquery" to listOf("c"),
        "entities" to listOf(
          Memo(
            code = "a",
            rowId = 2,
            title = "Trip",
            text = "Cross the desert by night"
          )
        )
      )
    )
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
      when {
        supportsSqlite(
          version = sqliteVersion(),
          minor = 39
        ) -> listOf(
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
