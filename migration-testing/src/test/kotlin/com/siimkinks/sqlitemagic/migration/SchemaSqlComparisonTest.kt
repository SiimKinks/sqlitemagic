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
  fun `schemas compare their table, index, virtual table and trigger SQL only`() {
    val snapshot = ReleaseSchemaSnapshot(
      formatVersion = 1,
      databaseId = "app",
      releaseVariant = "release",
      version = 2,
      modules = listOf("app"),
      configuration = MigrationConfiguration(foreignKeysEnabled = false),
      tableSql = listOf("""CREATE TABLE "note" ("id" INTEGER PRIMARY KEY, "body" TEXT)"""),
      views = emptyList(),
      indexSql = listOf("""CREATE INDEX "note_body" ON "note" ("body")"""),
      virtualTableSql = listOf("""CREATE VIRTUAL TABLE "note_fts" USING fts5("body")"""),
      triggerSql = listOf("""CREATE TRIGGER "note_fts_ai" AFTER INSERT ON "note" BEGIN SELECT 1; END""")
    )
    val cases = mapOf(
      "reformatted SQL" to snapshot.copy(
        tableSql = listOf("CREATE TABLE note (id INTEGER PRIMARY KEY, body TEXT)"),
        triggerSql = listOf("CREATE TRIGGER note_fts_ai AFTER INSERT ON note BEGIN SELECT 1; END")
      ),
      "other version and configuration" to snapshot.copy(
        version = 3,
        configuration = MigrationConfiguration(foreignKeysEnabled = true)
      ),
      "different table" to snapshot.copy(tableSql = listOf("""CREATE TABLE "note" ("id" INTEGER PRIMARY KEY)""")),
      "different index" to snapshot.copy(indexSql = emptyList()),
      "different virtual table" to snapshot.copy(
        virtualTableSql = listOf("""CREATE VIRTUAL TABLE "note_fts" USING fts5("title")""")
      ),
      "different trigger" to snapshot.copy(triggerSql = emptyList())
    )

    assertThat(
      cases.mapValues { (_, other) ->
        SchemaSqlComparison.sameSchema(
          first = snapshot,
          second = other
        )
      }
    ).isEqualTo(
      mapOf(
        "reformatted SQL" to true,
        "other version and configuration" to true,
        "different table" to false,
        "different index" to false,
        "different virtual table" to false,
        "different trigger" to false
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
