package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class WindowSelectTest {
  @Test
  fun `ranking functions number rows within their windows`() {
    val byAuthor = ORDER_BY(BOOK.AUTHOR.ASC())
    val query = SELECT
      .COLUMNS(
        BOOK.TITLE,
        ROW_NUMBER() OVER PARTITION_BY(BOOK.AUTHOR).ORDER_BY(BOOK.ID.ASC()),
        RANK() OVER byAuthor,
        DENSE_RANK() OVER byAuthor
      )
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())

    assertThat(query.compile().sql).isEqualTo(
      """SELECT "book"."title", ROW_NUMBER() OVER (PARTITION BY "book"."author" ORDER BY "book"."id" ASC), """ +
          """RANK() OVER (ORDER BY "book"."author" ASC), DENSE_RANK() OVER (ORDER BY "book"."author" ASC) """ +
          """FROM "main"."book" ORDER BY "book"."id" ASC"""
    )
    libraryDatabaseWithNotes().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row4("Dune", 1L, 1L, 1L),
          Row4("Emma", 1L, 3L, 2L),
          Row4("Kernel", 1L, 4L, 3L),
          Row4("Notes", 2L, 1L, 1L)
        )
      )
    }
  }

  @Test
  fun `aggregates over windows and ROWS frames keep their result types`() {
    val offset = PERSON.ID.asParameter("offset")
    val query = SELECT
      .COLUMNS(
        PERSON.ID,
        SUM(PERSON.AGE) OVER ORDER_BY(PERSON.ID.ASC()).ROWS_BETWEEN(1.PRECEDING).AND(CURRENT_ROW),
        COUNT().OVER(),
        MAX(PERSON.NAME) OVER PARTITION_BY(PERSON.AGE),
        TOTAL(PERSON.AGE) OVER ROWS_BETWEEN(UNBOUNDED_PRECEDING).AND(UNBOUNDED_FOLLOWING),
        COUNT() OVER ORDER_BY(PERSON.ID.ASC()).ROWS_BETWEEN(CURRENT_ROW).AND(offset.FOLLOWING)
      )
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val plan = query.compile()

    assertThat(plan.sql).isEqualTo(
      """SELECT "person"."id", SUM("person"."age") OVER (ORDER BY "person"."id" ASC ROWS BETWEEN """ +
          "? PRECEDING AND CURRENT ROW), COUNT(*) OVER (), " +
          """MAX("person"."name") OVER (PARTITION BY "person"."age"), """ +
          """TOTAL("person"."age") OVER (ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING), """ +
          """COUNT(*) OVER (ORDER BY "person"."id" ASC ROWS BETWEEN CURRENT ROW AND ? FOLLOWING) """ +
          """FROM "main"."person" ORDER BY "person"."id" ASC"""
    )
    personDatabase().use { database ->
      assertThat(
        query
          .usingConnection(database.dbConnection())
          .compile()
          .bind(offset TO 2L)
          .execute()
      ).isEqualTo(
        listOf(
          Row6(1L, 36L, 4L, "Ada", 155.0, 3L),
          Row6(2L, 121L, 4L, "Grace", 155.0, 3L),
          Row6(3L, 102L, 4L, "Linus", 155.0, 2L),
          Row6(4L, 34L, 4L, "Linus", 155.0, 1L)
        )
      )
    }
  }

  @Test
  fun `filters keep their aggregate's type and empty result and precede OVER`() {
    val adult = PERSON.AGE GREATER_THAN 20
    val totals = SELECT
      .COLUMNS(
        COUNT() FILTER_WHERE adult,
        SUM(PERSON.AGE) FILTER_WHERE (PERSON.AGE GREATER_THAN 100),
        GROUP_CONCAT(PERSON.NAME, ", ") FILTER_WHERE adult
      )
      .FROM(PERSON)
    val running = SELECT
      .COLUMN(COUNT() FILTER_WHERE adult OVER ORDER_BY(PERSON.ID.ASC()))
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())

    assertThat(
      listOf(
        totals.compile(),
        running.compile()
      )
        .map(::snapshot)
    ).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """SELECT COUNT(*) FILTER (WHERE "person"."age" > ?), SUM("person"."age") FILTER (WHERE """ +
              """"person"."age" > ?), GROUP_CONCAT("person"."name", ?) FILTER (WHERE "person"."age" > ?) """ +
              """FROM "main"."person"""",
          arguments = listOf(
            NativeValue.Integer(20L),
            NativeValue.Integer(100L),
            NativeValue.Text(", "),
            NativeValue.Integer(20L)
          )
        ),
        PlanSnapshot(
          sql = """SELECT COUNT(*) FILTER (WHERE "person"."age" > ?) OVER (ORDER BY "person"."id" ASC) """ +
              """FROM "main"."person" ORDER BY "person"."id" ASC""",
          arguments = listOf(NativeValue.Integer(20L))
        )
      )
    )
    personDatabase().use { database ->
      assertThat(
        listOf(
          totals.rows(database),
          running.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(Row3(2L, null, "Ada, Grace")),
          listOf(1L, 2L, 2L, 2L)
        )
      )
    }
  }

  @Test
  fun `windows rank inside derived sources and order results`() {
    val latest = ROW_NUMBER() OVER PARTITION_BY(BOOK.AUTHOR).ORDER_BY(BOOK.ID.DESC())
    val ranked = SELECT
      .COLUMNS(BOOK.TITLE, latest)
      .FROM(BOOK) AS "ranked"
    val newestPerAuthor = SELECT
      .COLUMN(ranked[BOOK.TITLE])
      .FROM(ranked)
      .WHERE(ranked[latest] IS 1L)
      .ORDER_BY(ranked[BOOK.TITLE].ASC())
    val byTitle = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .ORDER_BY((ROW_NUMBER() OVER ORDER_BY(BOOK.TITLE.DESC())).ASC())

    libraryDatabaseWithNotes().use { database ->
      assertThat(
        listOf(
          newestPerAuthor.rows(database),
          byTitle.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf("Emma", "Kernel", "Notes"),
          listOf("Notes", "Kernel", "Emma", "Dune")
        )
      )
    }
  }

  @Test
  fun `window misuse is SQLite's error`() {
    val inWhere = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE((ROW_NUMBER() OVER ORDER_BY(BOOK.ID.ASC())) GREATER_THAN 1L)
    val distinctWindow = SELECT
      .COLUMN(COUNT(DISTINCT(BOOK.AUTHOR)).OVER())
      .FROM(BOOK)

    libraryDatabase().use { database ->
      assertThat(
        listOf(
          sqliteErrorMessage { inWhere.rows(database) },
          sqliteErrorMessage { distinctWindow.rows(database) }
        )
      ).isEqualTo(
        listOf(
          "misuse of window function ROW_NUMBER()",
          "DISTINCT is not supported for window functions"
        )
      )
    }
  }
}
