package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.absoluteValue

internal class Fts5FunctionTest {
  private val indexJoin = """INNER JOIN "main"."article_fts" ON "article_fts"."rowid" = "article"."id""""
  private val operand = """"article_fts"."article_fts""""

  @Test
  fun `functions take the position of their column and read the search`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val sql: String,
      val rows: List<Any?>
    )

    val found = ARTICLE_FTS AS "found"
    fun ranked(ranking: SqlExpression<*, *, *>) = SELECT
      .COLUMN(ARTICLE.ID)
      .FROM(ARTICLE)
      .WHERE(ARTICLE_FTS MATCH "sqlite")
      .ORDER_BY(ranking.ASC())
    val cases = listOf(
      Case(
        label = "highlighted column",
        query = SELECT
          .COLUMN(HIGHLIGHT(ARTICLE_FTS.TITLE, "[", "]"))
          .FROM(ARTICLE)
          .WHERE(ARTICLE_FTS MATCH "sqlite OR room")
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT HIGHLIGHT($operand, 0, ?, ?) FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "article"."id" ASC""",
        rows = listOf("[SQLite] search", "[Room] guide", "Kotlin notes")
      ),
      Case(
        label = "highlighted nullable column after an unindexed one",
        query = SELECT
          .COLUMN(HIGHLIGHT(ARTICLE_FTS.SUMMARY, "[", "]"))
          .FROM(ARTICLE)
          .WHERE(ARTICLE_FTS MATCH "sqlite OR room")
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT HIGHLIGHT($operand, 2, ?, ?) FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "article"."id" ASC""",
        rows = listOf("Ranking rows", null, "[sqlite] from kotlin, [sqlite] everywhere and in every app")
      ),
      Case(
        label = "snippet of a column",
        query = SELECT
          .COLUMN(SNIPPET(ARTICLE_FTS.SUMMARY, "[", "]", "…", 4))
          .FROM(ARTICLE)
          .WHERE(ARTICLE_FTS MATCH "sqlite OR room")
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT SNIPPET($operand, 2, ?, ?, ?, ?) FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "article"."id" ASC""",
        rows = listOf("Ranking rows", null, "[sqlite] from kotlin, [sqlite]…")
      ),
      Case(
        label = "snippet of the best matching column",
        query = SELECT
          .COLUMN(SNIPPET(ARTICLE_FTS, "[", "]", "…", 3))
          .FROM(ARTICLE)
          .WHERE(ARTICLE_FTS MATCH "sqlite OR room")
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT SNIPPET($operand, -1, ?, ?, ?, ?) FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "article"."id" ASC""",
        rows = listOf("[SQLite] search", "[Room] guide", "[sqlite] from kotlin…")
      ),
      Case(
        label = "highlighted column without a search",
        query = SELECT
          .COLUMN(HIGHLIGHT(ARTICLE_FTS.SUMMARY, "[", "]"))
          .FROM(ARTICLE)
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT HIGHLIGHT($operand, 2, ?, ?) FROM "main"."article" $indexJoin ORDER BY "article"."id" ASC""",
        rows = listOf("Ranking rows", null, "sqlite from kotlin, sqlite everywhere and in every app")
      ),
      Case(
        label = "snippet of a column without a search",
        query = SELECT
          .COLUMN(SNIPPET(ARTICLE_FTS.SUMMARY, "[", "]", "…", 4))
          .FROM(ARTICLE)
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT SNIPPET($operand, 2, ?, ?, ?, ?) FROM "main"."article" $indexJoin """ +
            """ORDER BY "article"."id" ASC""",
        rows = listOf("Ranking rows", null, "sqlite from kotlin, sqlite…")
      ),
      Case(
        label = "snippet of the best matching column without a search reads the first column",
        query = SELECT
          .COLUMN(SNIPPET(SUMMARY_FTS, "[", "]", "…", 3))
          .FROM(ARTICLE)
          .ORDER_BY(ARTICLE.ID.ASC()),
        sql = """SELECT SNIPPET("summary_fts"."summary_fts", -1, ?, ?, ?, ?) FROM "main"."article" INNER JOIN """ +
            """"main"."summary_fts" ON "summary_fts"."rowid" = "article"."id" ORDER BY "article"."id" ASC""",
        rows = listOf("Ranking rows", null, "sqlite from kotlin…")
      ),
      Case(
        label = "unweighted rank",
        query = ranked(BM25(ARTICLE_FTS)),
        sql = """SELECT "article"."id" FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY BM25($operand) ASC""",
        rows = listOf(1L, 3L)
      ),
      Case(
        label = "weight of a later column fills the earlier ones",
        query = ranked(BM25(ARTICLE_FTS.SUMMARY to 10.0)),
        sql = """SELECT "article"."id" FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY BM25($operand, 1.0, 1.0, ?) ASC""",
        rows = listOf(3L, 1L)
      ),
      Case(
        label = "weights in any order",
        query = ranked(BM25(ARTICLE_FTS.SUMMARY to 2.0, ARTICLE_FTS.TITLE to 0.0)),
        sql = """SELECT "article"."id" FROM "main"."article" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY BM25($operand, ?, 1.0, ?) ASC""",
        rows = listOf(3L, 1L)
      ),
      Case(
        label = "aliased index",
        query = SELECT
          .COLUMN(HIGHLIGHT(found.TITLE, "[", "]"))
          .FROM(ARTICLE)
          .INNER_JOIN(found)
          .ON(found.ROWID IS ARTICLE.ID)
          .WHERE(found MATCH "room")
          .ORDER_BY(BM25(found.TITLE to 2.0).ASC()),
        sql = """SELECT HIGHLIGHT("found"."article_fts", 0, ?, ?) FROM "main"."article" INNER JOIN """ +
            """"main"."article_fts" AS "found" ON "found"."rowid" = "article"."id" WHERE "found"."article_fts" """ +
            """MATCH ? ORDER BY BM25("found"."article_fts", ?) ASC""",
        rows = listOf("[Room] guide")
      )
    )

    val results = articleDatabase().use { database ->
      cases.map { case -> case.label to (case.query.compile().sql to case.query.rows(database)) }
    }

    assertThat(results).isEqualTo(cases.map { it.label to (it.sql to it.rows) })
  }

  @Test
  fun `weights of a column twice, of another index or NaN reject before SQL`() {
    val found = ARTICLE_FTS AS "found"

    assertThat(
      listOf(
        { BM25(ARTICLE_FTS.TITLE to 1.0, ARTICLE_FTS.SUMMARY to 2.0, ARTICLE_FTS.TITLE to 3.0) },
        { BM25(ARTICLE_FTS.TITLE to 1.0, found.SUMMARY to 2.0) },
        { BM25(ARTICLE_FTS.TITLE to Double.NaN) }
      ).map { weights ->
        assertThrows(IllegalArgumentException::class.java) { weights() }.message
      }
    ).isEqualTo(
      listOf(
        "BM25 weighs article_fts.title twice",
        "BM25 weighs the columns of one full-text index, not of both article_fts and found",
        "NaN cannot be encoded as a required SQL REAL value"
      )
    )
  }

  @Test
  fun `ranks without a search are zero while RANK is NULL`() {
    val ranks = articleDatabase().use { database ->
      SELECT
        .COLUMNS(BM25(ARTICLE_FTS), BM25(ARTICLE_FTS.SUMMARY to 10.0), ARTICLE_FTS.RANK)
        .FROM(ARTICLE)
        .ORDER_BY(ARTICLE.ID.ASC())
        .rows(database)
        .map { (unweighted, weighted, rank) -> Row3(unweighted.absoluteValue, weighted.absoluteValue, rank) }
    }

    assertThat(ranks).isEqualTo(List(3) { Row3(0.0, 0.0, null) })
  }

  @Test
  fun `functions fail for a row that a left join of the index fills with NULLs`() {
    val found = ARTICLE_FTS AS "found"
    val functions = listOf<SqlExpression<*, *, *>>(
      HIGHLIGHT(found.TITLE, "[", "]").nullable(),
      SNIPPET(found.TITLE, "[", "]", "…", 3).nullable(),
      SNIPPET(found, "[", "]", "…", 3),
      BM25(found).nullable(),
      BM25(found.TITLE to 2.0).nullable()
    )

    val messages = articleDatabase().use { database ->
      functions.map { function ->
        sqliteErrorMessage {
          SELECT
            .COLUMN(function)
            .FROM(ARTICLE)
            .LEFT_JOIN(found)
            .ON((found.ROWID IS ARTICLE.ID) AND (found MATCH "room"))
            .rows(database)
        }
      }
    }

    assertThat(messages).isEqualTo(List(functions.size) { "no such cursor: 0" })
  }

  @Test
  fun `commands rebuild, optimize and check the index of the table`() {
    val commands = listOf(
      ARTICLE_FTS.rebuild(),
      ARTICLE_FTS.optimize(),
      ARTICLE_FTS.integrityCheck(),
      (ARTICLE_FTS AS "found").rebuild()
    )

    assertThat(commands.map { it.compile().sql }).isEqualTo(
      listOf(
        """INSERT INTO "main"."article_fts"("article_fts") VALUES ('rebuild')""",
        """INSERT INTO "main"."article_fts"("article_fts") VALUES ('optimize')""",
        """INSERT INTO "main"."article_fts"("article_fts", "rank") VALUES ('integrity-check', 1)""",
        """INSERT INTO "main"."article_fts"("article_fts") VALUES ('rebuild')"""
      )
    )
  }

  @Test
  fun `a rebuild repairs an index that misses rows of its table and notifies the table`() {
    val search = SELECT
      .COLUMN(ARTICLE.ID)
      .FROM(ARTICLE)
      .WHERE(ARTICLE_FTS MATCH "swift")

    data class Repair(
      val staleRows: List<Long>,
      val staleCheck: String?,
      val changes: List<Int>,
      val rows: List<Long>,
      val triggers: List<Set<String>>
    )

    val repair = articleDatabase().use { database ->
      database.execSQL("INSERT INTO article VALUES (4, 'Swift', 'lang', NULL)")
      val recorder = database.connection.recordTriggers()
      fun MutationQuery.run() = usingConnection(database.connection).execute()
      Repair(
        staleRows = search.rows(database),
        staleCheck = sqliteErrorMessage { ARTICLE_FTS.integrityCheck().run() },
        changes = listOf(ARTICLE_FTS.rebuild(), ARTICLE_FTS.integrityCheck(), ARTICLE_FTS.optimize()).map { it.run() },
        rows = search.rows(database),
        triggers = recorder.triggers
      )
    }

    assertThat(repair).isEqualTo(
      Repair(
        staleRows = emptyList(),
        staleCheck = "database disk image is malformed",
        changes = listOf(1, 1, 1),
        rows = listOf(4L),
        triggers = listOf(setOf("article"))
      )
    )
  }

  @Test
  fun `terms quote free text as phrases that every matching row contains`() {
    data class Case(
      val label: String,
      val text: String,
      val prefix: Boolean,
      val query: String,
      val ids: List<Long>
    )

    val cases = listOf(
      Case(
        label = "words",
        text = "sqlite search",
        prefix = false,
        query = """"sqlite" "search"""",
        ids = listOf(1L)
      ),
      Case(
        label = "whitespace of any kind and length",
        text = "\t sqlite \n search  ",
        prefix = false,
        query = """"sqlite" "search"""",
        ids = listOf(1L)
      ),
      Case(
        label = "operators as words",
        text = "sqlite OR room",
        prefix = false,
        query = """"sqlite" "OR" "room"""",
        ids = emptyList()
      ),
      Case(
        label = "column filter and prefix syntax as words",
        text = "title:room ^room sqli*",
        prefix = false,
        query = """"title:room" "^room" "sqli*"""",
        ids = emptyList()
      ),
      Case(
        label = "quotes doubled",
        text = """a"b""",
        prefix = false,
        query = """"a""b"""",
        ids = emptyList()
      ),
      Case(
        label = "punctuation that tokenizes to nothing",
        text = "sqlite -",
        prefix = false,
        query = """"sqlite" "-"""",
        ids = listOf(1L, 3L)
      ),
      Case(
        label = "NUL between words",
        text = "sqlite\u0000search",
        prefix = false,
        query = """"sqlite" "search"""",
        ids = listOf(1L)
      ),
      Case(
        label = "blank",
        text = " \t",
        prefix = false,
        query = """""""",
        ids = emptyList()
      ),
      Case(
        label = "prefix of the last term",
        text = "sqlite sea",
        prefix = true,
        query = """"sqlite" "sea"*""",
        ids = listOf(1L)
      ),
      Case(
        label = "prefix after trailing whitespace",
        text = "kot ",
        prefix = true,
        query = """"kot"*""",
        ids = listOf(3L)
      ),
      Case(
        label = "prefix of blank",
        text = "",
        prefix = true,
        query = """""""",
        ids = emptyList()
      )
    )

    val results = articleDatabase().use { database ->
      cases.map { case ->
        val query = ARTICLE_FTS.terms(
          text = case.text,
          prefix = case.prefix
        )
        val ids = SELECT
          .COLUMN(ARTICLE.ID)
          .FROM(ARTICLE)
          .WHERE(ARTICLE_FTS MATCH query)
          .ORDER_BY(ARTICLE.ID.ASC())
          .rows(database)
        case.label to (query to ids)
      }
    }

    assertThat(results).isEqualTo(cases.map { it.label to (it.query to it.ids) })
  }
}

@Suppress("PropertyName", "TestFunctionName")
private class ArticleTable(alias: String? = null) : Table<Unit>(
  name = "article",
  alias = alias
) {
  val ID = Column<Unit, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )

  override infix fun AS(alias: String) = ArticleTable(alias)
}

private val ARTICLE = ArticleTable()

@Suppress("PropertyName", "TestFunctionName")
private class ArticleFtsTable(alias: String? = null) : Fts5Table(
  name = "article_fts",
  alias = alias,
  contentTable = ARTICLE,
  contentRowIdColumnName = "id"
) {
  val TITLE = Fts5Column<String, Required, String, String, Required, String>(
    table = fieldTable,
    name = "title",
    position = 0,
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val SUMMARY = Fts5Column(
    table = fieldTable,
    name = "summary",
    position = 2,
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getText)
  )
  val ROWID = fullTextRowId(fieldTable)

  override infix fun AS(alias: String) = ArticleFtsTable(alias)
}

private val ARTICLE_FTS = ArticleFtsTable()

@Suppress("TestFunctionName")
private class SummaryFtsTable(alias: String? = null) : Fts5Table(
  name = "summary_fts",
  alias = alias,
  contentTable = ARTICLE,
  contentRowIdColumnName = "id"
) {
  override infix fun AS(alias: String) = SummaryFtsTable(alias)
}

private val SUMMARY_FTS = SummaryFtsTable()

private fun articleDatabase() = TestDatabase(
  "CREATE TABLE article (id INTEGER PRIMARY KEY, title TEXT NOT NULL, tag TEXT NOT NULL, summary TEXT)",
  "INSERT INTO article VALUES (1, 'SQLite search', 'db', 'Ranking rows'), (2, 'Room guide', 'sqlite', NULL), " +
      "(3, 'Kotlin notes', 'lang', 'sqlite from kotlin, sqlite everywhere and in every app')",
  "CREATE VIRTUAL TABLE article_fts USING fts5(title, tag UNINDEXED, summary, content='article', " +
      "content_rowid='id')",
  "INSERT INTO article_fts(article_fts) VALUES ('rebuild')",
  "CREATE VIRTUAL TABLE summary_fts USING fts5(summary, title, content='article', content_rowid='id')",
  "INSERT INTO summary_fts(summary_fts) VALUES ('rebuild')"
)
