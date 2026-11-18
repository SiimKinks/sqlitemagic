package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Test

internal class Fts4FunctionTest {
  private val indexJoin = """INNER JOIN "main"."paper_fts" ON "paper_fts"."rowid" = "paper"."id""""
  private val operand = """"paper_fts"."paper_fts""""

  @Test
  fun `functions take the position of their column and read the search`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val sql: String,
      val rows: List<Any?>
    )

    val found = PAPER_FTS AS "found"
    fun searched(function: SqlExpression<*, *, *>, search: SqlExpression<Boolean, *, *> = PAPER_FTS MATCH "sqlite") =
      SELECT
        .COLUMN(function)
        .FROM(PAPER)
        .WHERE(search)
        .ORDER_BY(PAPER.ID.ASC())
    val cases = listOf(
      Case(
        label = "snippet of a column",
        query = searched(SNIPPET(PAPER_FTS.SUMMARY, "[", "]", "…", 4)),
        sql = """SELECT SNIPPET($operand, ?, ?, ?, 2, ?) FROM "main"."paper" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = listOf(null, "[sqlite] from kotlin, [sqlite]…")
      ),
      Case(
        label = "snippet of the best matching column",
        query = searched(SNIPPET(PAPER_FTS, "[", "]", "…", 3)),
        sql = """SELECT SNIPPET($operand, ?, ?, ?, -1, ?) FROM "main"."paper" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = listOf("[SQLite] search", "[sqlite] from kotlin…")
      ),
      Case(
        label = "offsets",
        query = searched(OFFSETS(PAPER_FTS)),
        sql = """SELECT OFFSETS($operand) FROM "main"."paper" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = listOf("0 0 0 6", "2 0 0 6 2 0 20 6")
      ),
      Case(
        label = "matchinfo in its default format",
        query = searched(MATCHINFO(PAPER_FTS)),
        sql = """SELECT MATCHINFO($operand) FROM "main"."paper" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = listOf(44, 44)
      ),
      Case(
        label = "matchinfo in another format",
        query = searched(MATCHINFO(PAPER_FTS, "pcnalx")),
        sql = """SELECT MATCHINFO($operand, ?) FROM "main"."paper" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = listOf(72, 72)
      ),
      Case(
        label = "search of a language",
        query = searched(
          function = PAPER.ID,
          search = (PAPER_FTS MATCH "sqlite") AND (PAPER_FTS.LANGUAGE IS 1)
        ),
        sql = """SELECT "paper"."id" FROM "main"."paper" $indexJoin WHERE ($operand MATCH ?) AND """ +
            """("paper_fts"."language" = ?) ORDER BY "paper"."id" ASC""",
        rows = listOf(2L)
      ),
      Case(
        label = "unindexed column is not searched",
        query = searched(
          function = PAPER.ID,
          search = PAPER_FTS MATCH "db"
        ),
        sql = """SELECT "paper"."id" FROM "main"."paper" $indexJoin WHERE $operand MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = emptyList()
      ),
      Case(
        label = "functions without a search",
        query = SELECT
          .COLUMNS(SNIPPET(PAPER_FTS.TITLE, "[", "]", "…", 4), OFFSETS(PAPER_FTS))
          .FROM(PAPER)
          .ORDER_BY(PAPER.ID.ASC()),
        sql = """SELECT SNIPPET($operand, ?, ?, ?, 0, ?), OFFSETS($operand) FROM "main"."paper" $indexJoin """ +
            """ORDER BY "paper"."id" ASC""",
        rows = List(4) { Row2("", "") }
      ),
      Case(
        label = "aliased index",
        query = SELECT
          .COLUMN(SNIPPET(found.TITLE, "[", "]", "…", 4))
          .FROM(PAPER)
          .INNER_JOIN(found)
          .ON(found.ROWID IS PAPER.ID)
          .WHERE(found MATCH "room")
          .ORDER_BY(PAPER.ID.ASC()),
        sql = """SELECT SNIPPET("found"."paper_fts", ?, ?, ?, 0, ?) FROM "main"."paper" INNER JOIN """ +
            """"main"."paper_fts" AS "found" ON "found"."rowid" = "paper"."id" WHERE "found"."paper_fts" MATCH ? """ +
            """ORDER BY "paper"."id" ASC""",
        rows = listOf("Title: [room] - guide")
      )
    )

    val results = paperDatabase().use { database ->
      cases.map { case ->
        val rows = case.query
          .rows(database)
          .map { row ->
            when (row) {
              is ByteArray -> row.size
              else -> row
            }
          }
        case.label to (case.query.compile().sql to rows)
      }
    }

    assertThat(results).isEqualTo(cases.map { it.label to (it.sql to it.rows) })
  }

  @Test
  fun `functions fail for a row that a left join of the index fills with NULLs`() {
    val found = PAPER_FTS AS "found"
    val functions = listOf<SqlExpression<*, *, *>>(
      SNIPPET(found.TITLE, "[", "]", "…", 3).nullable(),
      SNIPPET(found, "[", "]", "…", 3),
      OFFSETS(found).nullable(),
      MATCHINFO(found).nullable()
    )

    val messages = paperDatabase().use { database ->
      functions.map { function ->
        sqliteErrorMessage {
          SELECT
            .COLUMN(function)
            .FROM(PAPER)
            .LEFT_JOIN(found)
            .ON((found.ROWID IS PAPER.ID) AND (found MATCH "room"))
            .rows(database)
        }
      }
    }

    assertThat(messages).isEqualTo(
      listOf(
        "illegal first argument to snippet",
        "illegal first argument to snippet",
        "illegal first argument to offsets",
        "illegal first argument to matchinfo"
      )
    )
  }

  @Test
  fun `commands rebuild, optimize and check the index of the table`() {
    val commands = listOf(PAPER_FTS.rebuild(), PAPER_FTS.optimize(), PAPER_FTS.integrityCheck())

    val changes = paperDatabase().use { database ->
      commands.map {
        it
          .usingConnection(database.connection)
          .execute()
      }
    }

    assertThat(commands.map { it.compile().sql } to changes).isEqualTo(
      listOf(
        """INSERT INTO "main"."paper_fts"("paper_fts") VALUES ('rebuild')""",
        """INSERT INTO "main"."paper_fts"("paper_fts") VALUES ('optimize')""",
        """INSERT INTO "main"."paper_fts"("paper_fts") VALUES ('integrity-check')"""
      ) to listOf(1, 1, 1)
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
        label = "column filter and exclusion syntax as words",
        text = "title:room -guide",
        prefix = false,
        query = """"title:room" "-guide"""",
        ids = listOf(4L)
      ),
      Case(
        label = "star separates words",
        text = "sqlite*search",
        prefix = false,
        query = """"sqlite search"""",
        ids = listOf(1L)
      ),
      Case(
        label = "quotes around terms",
        text = """"sqlite search"""",
        prefix = false,
        query = """" sqlite" "search """",
        ids = listOf(1L)
      ),
      Case(
        label = "quote separates words of one phrase",
        text = """sqlite"search""",
        prefix = false,
        query = """"sqlite search"""",
        ids = listOf(1L)
      ),
      Case(
        label = "quote keeps the phrase order",
        text = """search"sqlite""",
        prefix = false,
        query = """"search sqlite"""",
        ids = emptyList()
      ),
      Case(
        label = "letter outside the basic plane",
        text = "𠮷",
        prefix = false,
        query = """"𠮷"""",
        ids = emptyList()
      ),
      Case(
        label = "punctuation without words left out",
        text = "sqlite - * ...",
        prefix = false,
        query = """"sqlite"""",
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
        query = "",
        ids = emptyList()
      ),
      Case(
        label = "prefix of the last term",
        text = "sqlite sea",
        prefix = true,
        query = """"sqlite" "sea*"""",
        ids = listOf(1L)
      ),
      Case(
        label = "prefix after trailing punctuation",
        text = "kot. -",
        prefix = true,
        query = """"kot*"""",
        ids = listOf(3L)
      ),
      Case(
        label = "prefix after an opening quote",
        text = """"sea""",
        prefix = true,
        query = """" sea*"""",
        ids = listOf(1L)
      ),
      Case(
        label = "prefix ending in a letter outside the basic plane",
        text = "ab𠮷.",
        prefix = true,
        query = """"ab𠮷*"""",
        ids = emptyList()
      ),
      Case(
        label = "prefix of blank",
        text = "",
        prefix = true,
        query = "",
        ids = emptyList()
      )
    )

    val results = paperDatabase().use { database ->
      cases.map { case ->
        val query = PAPER_FTS.terms(
          text = case.text,
          prefix = case.prefix
        )
        val ids = SELECT
          .COLUMN(PAPER.ID)
          .FROM(PAPER)
          .WHERE(PAPER_FTS MATCH query)
          .ORDER_BY(PAPER.ID.ASC())
          .rows(database)
        case.label to (query to ids)
      }
    }

    assertThat(results).isEqualTo(cases.map { it.label to (it.query to it.ids) })
  }
}

@Suppress("PropertyName", "TestFunctionName")
private class PaperTable(alias: String? = null) : Table<Unit>(
  name = "paper",
  alias = alias
) {
  val ID = Column<Unit, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )

  override infix fun AS(alias: String) = PaperTable(alias)
}

private val PAPER = PaperTable()

@Suppress("PropertyName", "TestFunctionName")
private class PaperFtsTable(alias: String? = null) : Fts4Table(
  name = "paper_fts",
  alias = alias,
  contentTable = PAPER,
  contentRowIdColumnName = "id"
) {
  val TITLE = Fts4Column<String, Required, String, String, Required, String>(
    table = fieldTable,
    name = "title",
    position = 0,
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val SUMMARY = Fts4Column(
    table = fieldTable,
    name = "summary",
    position = 2,
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getText)
  )
  val LANGUAGE = Column<Nothing, Int, Required, Int, Int, Required, Int>(
    table = fieldTable,
    name = "language",
    nullness = Required,
    parameterEncoding = INT.parameterEncoding()
  )
  val ROWID = fullTextRowId(fieldTable)

  override infix fun AS(alias: String) = PaperFtsTable(alias)
}

private val PAPER_FTS = PaperFtsTable()

private fun paperDatabase() = TestDatabase(
  "CREATE TABLE paper (id INTEGER PRIMARY KEY, title TEXT NOT NULL, tag TEXT NOT NULL, summary TEXT, " +
      "language INTEGER NOT NULL)",
  "INSERT INTO paper VALUES (1, 'SQLite search', 'db', NULL, 0), (2, 'Room guide', 'sqlite', 'sqlite room', 1), " +
      "(3, 'Kotlin notes', 'lang', 'sqlite from kotlin, sqlite everywhere and in every app', 0), " +
      "(4, 'Title: room - guide', 'lang', NULL, 0)",
  """CREATE VIRTUAL TABLE paper_fts USING fts4(title, tag, summary, content='paper', languageid="language", """ +
      """notindexed="tag")""",
  "INSERT INTO paper_fts(paper_fts) VALUES ('rebuild')"
)
