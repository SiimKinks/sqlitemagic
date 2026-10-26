package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class TableSchemaSqlTest {
  @Test
  fun `equal raw names and repeated sources keep their own schema with and without aliases`() {
    val main = testTable<Any>(name = "books")
    val temporary = testTable<Any>(
      name = "books",
      temporary = true
    )

    val sql = SELECT
      .COLUMN(1.asLiteral())
      .FROM(main.AS("first_main"))
      .INNER_JOIN(temporary.AS("first_temp"))
      .INNER_JOIN(main.AS("other_main"))
      .INNER_JOIN(temporary)
      .compile()
      .sql

    assertThat(sql).isEqualTo(
      """SELECT 1 FROM "main"."books" AS "first_main" """ +
          """INNER JOIN "temp"."books" AS "first_temp" """ +
          """INNER JOIN "main"."books" AS "other_main" INNER JOIN "temp"."books""""
    )
  }

  @Test
  fun `aliased tables read their declared schemas when both contain the same name`() {
    val rows = BundledSqliteDatabase().use { database ->
      database.execSQL("CREATE TABLE main.books (title TEXT NOT NULL)")
      database.execSQL("CREATE TEMP TABLE books (title TEXT NOT NULL)")
      database.execSQL("INSERT INTO main.books VALUES ('main title')")
      database.execSQL("INSERT INTO temp.books VALUES ('temp title')")
      val connection = database.dbConnection()
      listOf(false, true).map { temporary ->
        val books = testTable<Any>(
          name = "books",
          alias = "row",
          temporary = temporary
        )
        SELECT
          .COLUMN(
            testStringKey(
              table = books,
              name = "title"
            )
          )
          .FROM(books)
          .usingConnection(connection)
          .execute()
      }
    }

    assertThat(rows).isEqualTo(listOf(listOf("main title"), listOf("temp title")))
  }
}
