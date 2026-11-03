package com.siimkinks.sqlitemagic.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class SchemaSqlComparisonTest {
  @Test
  fun `statements compare equal regardless of identifier quotes, spacing and comments but not case`() {
    val quoted = """CREATE TABLE "order" ("id" INTEGER PRIMARY KEY, "note" TEXT DEFAULT 'A b')"""
    val cases = mapOf(
      "unquoted identifiers" to Pair(
        first = "CREATE TABLE order (id INTEGER PRIMARY KEY, note TEXT DEFAULT 'A b')",
        second = quoted
      ),
      "other quotes" to Pair(
        first = "CREATE TABLE [order](`id` INTEGER PRIMARY KEY, note TEXT DEFAULT 'A b')",
        second = quoted
      ),
      "spacing and comments" to Pair(
        first = """CREATE TABLE "order"( "id" INTEGER /* key */ PRIMARY KEY,
          "note" TEXT DEFAULT 'A b' )""",
        second = quoted
      ),
      "different literal" to Pair(
        first = """CREATE TABLE "order" ("id" INTEGER PRIMARY KEY, "note" TEXT DEFAULT 'a b')""",
        second = quoted
      ),
      "different identifier" to Pair(
        first = """CREATE TABLE "orders" ("id" INTEGER PRIMARY KEY, "note" TEXT DEFAULT 'A b')""",
        second = quoted
      ),
      "different identifier case" to Pair(
        first = """CREATE TABLE "Order" ("Id" INTEGER PRIMARY KEY, "note" TEXT DEFAULT 'A b')""",
        second = quoted
      ),
      "quoted identifier" to Pair(
        first = """CREATE TABLE "or der" ("id" INTEGER PRIMARY KEY, "note" TEXT DEFAULT 'A b')""",
        second = quoted
      ),
      "double-quoted default case" to Pair(
        first = """CREATE TABLE t (s TEXT DEFAULT "Draft")""",
        second = """CREATE TABLE t (s TEXT DEFAULT "draft")"""
      ),
      "bare default case" to Pair(
        first = "CREATE TABLE t (s TEXT DEFAULT Draft)",
        second = "CREATE TABLE t (s TEXT DEFAULT draft)"
      )
    )

    assertThat(
      cases.mapValues { (_, statements) ->
        SchemaSqlComparison.sameStatements(
        first = listOf(statements.first),
        second = listOf(statements.second)
        )
      }
    ).isEqualTo(
      mapOf(
        "unquoted identifiers" to true,
        "other quotes" to true,
        "spacing and comments" to true,
        "different literal" to false,
        "different identifier" to false,
        "different identifier case" to false,
        "quoted identifier" to false,
        "double-quoted default case" to false,
        "bare default case" to false
      )
    )
  }

  @Test
  fun `keys replace identifiers once and keep unreadable SQL as written`() {
    val cases = mapOf(
      "renamed identifiers" to SchemaSqlComparison.key(
        sql = """CREATE TABLE "Writers" (author INTEGER REFERENCES writers(id))""",
        identifiers = mapOf(
          "writers" to "authors",
          "authors" to "people"
        )
      ),
      "replacement that needs quotes" to SchemaSqlComparison.key(
        sql = "CREATE TABLE current (id INTEGER)",
        identifiers = mapOf("current" to "legacy name")
      ),
      "unterminated quote" to SchemaSqlComparison.key(sql = """CREATE TABLE "current""")
    )

    assertThat(cases).isEqualTo(
      mapOf(
        "renamed identifiers" to "CREATE TABLE authors ( author INTEGER REFERENCES authors ( id ) )",
        "replacement that needs quotes" to """CREATE TABLE "legacy name" ( id INTEGER )""",
        "unterminated quote" to """CREATE TABLE "current"""
      )
    )
  }
}
