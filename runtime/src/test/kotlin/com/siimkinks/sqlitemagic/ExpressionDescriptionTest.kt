package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class ExpressionDescriptionTest {
  private val emma = SELECT
    .COLUMN(BOOK.ID)
    .FROM(BOOK)
    .WHERE(BOOK.TITLE IS "Emma")

  private val titlesOfAuthor = SELECT
    .COLUMN(BOOK.TITLE)
    .FROM(BOOK)
    .WHERE(BOOK.AUTHOR IS AUTHOR.ID)

  private val authorNames = SELECT
    .COLUMN(AUTHOR.NAME)
    .FROM(BOOK)
    .asScalar()

  private val bookX = BOOK.ID.AS("x")

  private val authorX = AUTHOR.ID.AS("x")

  private val bookIds = SELECT
    .COLUMN(bookX)
    .FROM(BOOK)

  private val authorIdsDescending = SELECT
    .COLUMN(authorX)
    .FROM(AUTHOR)
    .ORDER_BY(AUTHOR.ID.DESC())

  private fun <T> selectPerBook(value: SqlExpression<*, *, T>) = SELECT
    .COLUMN(value)
    .FROM(BOOK)
    .ORDER_BY(BOOK.ID.ASC())

  private fun <T> selectBookIdAnd(books: Cte<Long>, value: SqlExpression<*, *, T>) = SELECT
    .COLUMNS(books[bookX], value)
    .FROM(books)
    .ORDER_BY(books[bookX].ASC())

  private fun authorIdsOf(authors: Cte<Long>) = SELECT
    .COLUMN(authors[authorX])
    .FROM(authors)

  @Test
  fun `each expression kind describes itself as the SQL of an operand`() {
    val sum = BOOK.ID + BOOK.AUTHOR
    val person = PERSON AS "p"
    val names = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON) AS "names"
    val minAge = PERSON.AGE.asParameter("min_age")
    val instr = scalarFunction("instr").readAs(LONG.nullable)
    val titleOfMatchingAuthor = CASE
      .WHEN(BOOK.ID IS BOOK.AUTHOR)
      .THEN(BOOK.TITLE)
      .ELSE(BOOK.TITLE)
      .END()
    val allBookIds = SELECT
      .COLUMN(BOOK.ID)
      .FROM(BOOK)
    val cases = listOf(
      "binary" to (sum to """("book"."id" + "book"."author")"""),
      "binary of an aliased occurrence" to ((person.ID + person.AGE) to """("p"."id" + "p"."age")"""),
      "binary with an output alias" to ((sum AS "total") to """("book"."id" + "book"."author")"""),
      "nested binary" to ((sum * BOOK.ID) to """(("book"."id" + "book"."author") * "book"."id")"""),
      "negate" to (-BOOK.ID to """(-("book"."id"))"""),
      "not" to (!(BOOK.ID IS BOOK.AUTHOR) to """(NOT ("book"."id" = "book"."author"))"""),
      "null test" to (AUTHOR.PUBLISHER.IS_NOT_NULL() to """("author"."publisher" IS NOT NULL)"""),
      "range" to ((BOOK.ID BETWEEN BOOK.AUTHOR).AND(BOOK.ID) to
          """("book"."id" BETWEEN "book"."author" AND "book"."id")"""),
      "membership" to (PERSON.ID.IN(PERSON.PARENT_ID, PERSON.ID) to
          """("person"."id" IN ("person"."parent_id", "person"."id"))"""),
      "row value" to ((row(BOOK.ID, BOOK.AUTHOR) GREATER_THAN row(BOOK.AUTHOR, BOOK.ID)) to
          """(("book"."id", "book"."author") > ("book"."author", "book"."id"))"""),
      "collate" to ((AUTHOR.NAME COLLATE NOCASE) to """"author"."name" COLLATE NOCASE"""),
      "cast" to (CAST(PERSON.ID AS TEXT) to """CAST("person"."id" AS TEXT)"""),
      "function call" to (LOWER(AUTHOR.NAME) to """LOWER("author"."name")"""),
      "case" to (titleOfMatchingAuthor to
          """CASE WHEN "book"."id" = "book"."author" THEN "book"."title" ELSE "book"."title" END"""),
      "aggregate" to (COUNT() to "COUNT(*)"),
      "aggregate of a column" to (SUM(PERSON.AGE) to """SUM("person"."age")"""),
      "window" to ((ROW_NUMBER() OVER PARTITION_BY(BOOK.AUTHOR).ORDER_BY(BOOK.ID.ASC())) to
          """ROW_NUMBER() OVER (PARTITION BY "book"."author" ORDER BY "book"."id" ASC)"""),
      "literal" to ("it's".asLiteral(STRING) to "'it''s'"),
      "raw" to ("abs(${BOOK.ID})".expr(LONG) to """(abs("book"."id"))"""),
      "subquery" to (allBookIds.asScalar() to """(SELECT "book"."id" FROM "main"."book")"""),
      "correlated subquery" to (titlesOfAuthor.asScalar() to
          """(SELECT "book"."title" FROM "main"."book" WHERE "book"."author" = "author"."id")"""),
      "subquery without its automatic join" to (authorNames to """(SELECT "author"."name" FROM "main"."book")"""),
      "exists" to (EXISTS(titlesOfAuthor) to
          """(EXISTS (SELECT "book"."title" FROM "main"."book" WHERE "book"."author" = "author"."id"))"""),
      "derived source column" to ((names[PERSON.ID] + names[PERSON.ID]) to
          """("names"."person.id" + "names"."person.id")"""),
      "bound value" to (7L.asValue() to "? {value}"),
      "binary with a bound value" to ((BOOK.ID + 1L) to """("book"."id" + ?) {value}"""),
      "values collapse" to (PERSON.AGE.IN(17, 85) to """("person"."age" IN (?, ?)) {value}"""),
      "escape" to ((BOOK.TITLE LIKE "x") ESCAPE "!" to """("book"."title" LIKE ? ESCAPE ?) {value}"""),
      "function call with a value" to (instr(BOOK.TITLE, ":") to """"instr"("book"."title", ?) {value}"""),
      "raw with arguments" to ("${BOOK.TITLE} LIKE ?".expr(BOOLEAN, "A%") to """("book"."title" LIKE ?) {value}"""),
      "subquery with a value" to (emma.asScalar() to
          """(SELECT "book"."id" FROM "main"."book" WHERE "book"."title" = ?) {value}"""),
      "parameter" to (minAge to "? {min_age}"),
      "value and parameter" to (((PERSON.AGE + 1) GREATER_THAN minAge) to
          """(("person"."age" + ?) > ?) {value, min_age}""")
    )

    assertThat(cases.associate { (label, case) -> label to case.first.toString() })
      .containsExactlyEntriesIn(cases.associate { (label, case) -> label to case.second })
  }

  @Test
  fun `a column of an unnamed common table expression stays unqualified inside a computed expression`() {
    val personId = PERSON.ID.AS("person_id")
    lateinit var column: SqlExpression<*, *, *>
    lateinit var computed: SqlExpression<*, *, *>
    WITH(
      SELECT
        .COLUMN(personId)
        .FROM(PERSON)
    ) { ids ->
      column = ids[personId]
      computed = ids[personId] + ids[personId]
      SELECT
        .COLUMN(ids[personId])
        .FROM(ids)
    }

    assertThat(listOf("$column", "$computed")).isEqualTo(
      listOf(
        """"person_id"""",
        """("person_id" + "person_id")"""
      )
    )
  }

  @Test
  fun `an unselected expression fails at lookup naming it`() {
    val cases = listOf(
      "computed" to (BOOK.ID + BOOK.AUTHOR to """("book"."id" + "book"."author") was not selected"""),
      "computed with a value" to (BOOK.ID + 1L to """("book"."id" + ?) {value} was not selected"""),
      "function call" to (LOWER(BOOK.TITLE) to """LOWER("book"."title") was not selected"""),
      "raw" to ("abs(${BOOK.ID})".expr(LONG) to """(abs("book"."id")) was not selected"""),
      "column" to (BOOK.AUTHOR to """"book"."author" was not selected""")
    )
    val row = SELECT
      .COLUMNS(BOOK.ID, AUTHOR.all())
      .FROM(BOOK)
      .rowsIn(::libraryDatabase)
      .first()

    assertThat(
      cases.associate { (label, case) ->
        label to assertThrows(IllegalArgumentException::class.java) { row[case.first] }.message
      }
    ).containsExactlyEntriesIn(cases.associate { (label, case) -> label to case.second })
  }

  @Test
  fun `a selection a query would reject still describes itself`() {
    val joined = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON(BOOK.AUTHOR IS AUTHOR.ID) AS "joined"
    val rejected = SELECT
      .COLUMN(joined[BOOK.TITLE])
      .FROM(joined)
      .asScalar()

    assertThat("$rejected").isEqualTo("""(SELECT "joined"."book.title" FROM (SELECT  {invalid}""")
    assertThat(assertThrows(IllegalArgumentException::class.java) { SELECT.COLUMN(rejected).compile() })
      .hasMessageThat()
      .isEqualTo("book.title is NOT NULL but an outer join can leave it NULL; select the field with .nullable()")
  }

  @Test
  fun `an expression without values interpolates as an operand of raw SQL`() {
    val joinedAuthorNames = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(BOOK)
      .LEFT_JOIN(AUTHOR)
      .ON(BOOK.AUTHOR IS AUTHOR.ID)
      .asScalar()
    val ownCte = WITH(authorIdsDescending, ::authorIdsOf).asScalar()
    val cases = listOf(
      "binary keeps its precedence" to (selectPerBook("${BOOK.ID + BOOK.AUTHOR} * 2".expr(LONG)) to
          listOf(4L, 8L, 12L)),
      "raw keeps its precedence" to (selectPerBook("${"${BOOK.ID} + 1".expr(LONG)} * 2".expr(LONG)) to
          listOf(4L, 6L, 8L)),
      "subquery stays correlated" to (
          SELECT
            .COLUMN("${titlesOfAuthor.asScalar()}".expr(STRING))
            .FROM(AUTHOR)
            .ORDER_BY(AUTHOR.ID.ASC()) to listOf("Dune", "Emma", "Kernel")
          ),
      "subquery keeps its explicit join" to (selectPerBook("$joinedAuthorNames".expr(STRING)) to
          listOf("Ada", "Ada", "Ada")),
      "subquery keeps its own automatically named common table expression" to (
          selectPerBook("$ownCte".expr(LONG)) to listOf(3L, 3L, 3L)
          ),
      "subquery's own automatic name leaves an outer common table expression visible" to (
          WITH(bookIds AS "cte1") { books ->
            val readsOuter = WITH(authorIdsDescending) {
              SELECT
                .COLUMN(books[bookX])
                .FROM(books)
            }
            selectBookIdAnd(books, "${readsOuter.asScalar()}".expr(LONG))
          } to listOf(Row2(1L, 1L), Row2(2L, 1L), Row2(3L, 1L))
          ),
      "subquery reads a common table expression named by its query" to (
          WITH(bookIds, authorIdsDescending AS "named") { books, authors ->
            selectBookIdAnd(books, "${authorIdsOf(authors).asScalar()}".expr(LONG))
          } to listOf(Row2(1L, 3L), Row2(2L, 3L), Row2(3L, 3L))
          )
    )

    libraryDatabase().use { database ->
      assertThat(cases.associate { (label, case) -> label to case.first.rows(database) })
        .containsExactlyEntriesIn(cases.associate { (label, case) -> label to case.second })
    }
  }

  @Test
  fun `an expression with values fails to prepare when interpolated instead of misbinding`() {
    val cases = listOf(
      "bound value" to 7L.asValue(),
      "binary with a bound value" to BOOK.ID + 1L,
      "raw with arguments" to "abs(?)".expr(LONG, 1L),
      "parameter" to BOOK.ID.asParameter("id"),
      "subquery with a value" to emma.asScalar()
    )

    libraryDatabase().use { database ->
      assertThat(
        cases.associate { (label, expression) ->
          label to sqliteErrorMessage {
            SELECT
              .COLUMN(BOOK.ID)
              .FROM(BOOK)
              .WHERE("$expression > ?".expr(BOOLEAN, 1L))
              .rows(database)
          }
        }
      ).containsExactlyEntriesIn(cases.associate { (label) -> label to """unrecognized token: "{"""" })
    }
  }

  @Test
  fun `a subquery fails to prepare when interpolated without the names of its sources`() {
    val noSuchCte = "no such table: {cte1}"
    val cases = listOf(
      "automatically named common table expression" to (
          WITH(bookIds, authorIdsDescending) { books, authors ->
            selectBookIdAnd(books, "${authorIdsOf(authors).asScalar()}".expr(LONG))
          } to noSuchCte
          ),
      "aliased automatically named common table expression" to (
          WITH(bookIds, authorIdsDescending) { books, authors ->
            selectBookIdAnd(books, "${authorIdsOf(authors AS "c").asScalar()}".expr(LONG))
          } to noSuchCte
          ),
      "outer name equal to an automatic name" to (
          WITH(bookIds AS "cte1", authorIdsDescending) { books, authors ->
            selectBookIdAnd(books, "${authorIdsOf(authors).asScalar()}".expr(LONG))
          } to noSuchCte
          ),
      "exists" to (
          WITH(bookIds, authorIdsDescending) { books, authors ->
            selectBookIdAnd(books, "${EXISTS(SELECT FROM authors)}".expr(BOOLEAN))
          } to noSuchCte
          ),
      "automatic join" to (selectPerBook("$authorNames".expr(STRING)) to "no such column: author.name")
    )

    libraryDatabase().use { database ->
      assertThat(cases.associate { (label, case) -> label to sqliteErrorMessage { case.first.rows(database) } })
        .containsExactlyEntriesIn(cases.associate { (label, case) -> label to case.second })
    }
  }

  @Test
  fun `describing an expression leaves its query and its value equality untouched`() {
    val raw = "${BOOK.TITLE} LIKE ?".expr(BOOLEAN, "A%")
    val computed = BOOK.ID + 1L
    val minId = BOOK.ID.asParameter("min_id")
    val query = SELECT
      .COLUMN(computed)
      .FROM(BOOK)
      .WHERE(raw AND (BOOK.ID GREATER_THAN minId))
    val before = observedSnapshot(query.compile().bind(minId TO 1L))

    assertThat(listOf("$raw", "$computed", "$minId")).isEqualTo(listOf("$raw", "$computed", "$minId"))
    assertThat(observedSnapshot(query.compile().bind(minId TO 1L))).isEqualTo(before)

    fun nodes() = listOf(
      ExpressionNode.Bound(NativeValue.Integer(7L)),
      ExpressionNode.Literal(NativeValue.Text("a")),
      ExpressionNode.Raw(
        sql = "1",
        arguments = listOf(NativeValue.Null),
        dependencies = QueryDependencies.NONE
      )
    )
    assertThat(nodes()).isEqualTo(nodes())
  }
}
