package com.siimkinks.sqlitemagic.manager

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class RowIdMigrationTest {
  @Test
  fun `adding or removing a row ID keeps every row ID, child row and a fresh-install schema`() {
    val cases = mapOf(
      "adding a row ID" to RowIdCase(
        previous = listOf(TEXT_PRIMARY_KEY, TITLE),
        current = listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE),
        rows = listOf(
          """INSERT INTO "note" ("id", "title") VALUES ('a', 'A'), ('b', 'B'), ('c', 'C'), ('d', 'D')""",
          """DELETE FROM "note" WHERE "id" = 'b'"""
        ),
        read = """SELECT "id" || ':' || "row_id" FROM "note" ORDER BY "id""""
      ),
      "removing the row ID" to RowIdCase(
        previous = listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE),
        current = listOf(TEXT_PRIMARY_KEY, TITLE),
        rows = listOf(
          """INSERT INTO "note" ("id", "row_id", "title") VALUES ('a', 1, 'A'), ('c', 3, 'C'), ('d', 4, 'D')"""
        ),
        read = """SELECT "id" || ':' || rowid FROM "note" ORDER BY "id""""
      ),
      "renaming the row ID column" to RowIdCase(
        previous = listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE),
        current = listOf(
          TEXT_UNIQUE_ID,
          ROW_ID.copy(
            name = "rid",
            schema = """"rid" INTEGER PRIMARY KEY"""
          ),
          TITLE
        ),
        rows = listOf(
          """INSERT INTO "note" ("id", "row_id", "title") VALUES ('a', 1, 'A'), ('c', 3, 'C'), ('d', 4, 'D')"""
        ),
        read = """SELECT "id" || ':' || "rid" FROM "note" ORDER BY "id""""
      ),
      "adding a row ID beside a column named rowid" to RowIdCase(
        previous = listOf(TEXT_PRIMARY_KEY, ROWID_NAMED_COLUMN, TITLE),
        current = listOf(TEXT_UNIQUE_ID, ROW_ID, ROWID_NAMED_COLUMN, TITLE),
        rows = listOf(
          """INSERT INTO "note" ("id", "rowid", "title") VALUES ('a', 'x', 'A'), ('b', 'y', 'B'), ('c', 'z', 'C'), """ +
              """('d', 'w', 'D')""",
          """DELETE FROM "note" WHERE "id" = 'b'"""
        ),
        read = """SELECT "id" || ':' || "row_id" || ':' || "rowid" FROM "note" ORDER BY "id""""
      )
    )
    val expected = mapOf(
      "adding a row ID" to MigratedRows(
        rows = listOf("a:1", "c:3", "d:4"),
        attachments = 4,
        matchesFreshInstall = true,
        warnings = emptyList()
      ),
      "removing the row ID" to MigratedRows(
        rows = listOf("a:1", "c:3", "d:4"),
        attachments = 4,
        matchesFreshInstall = true,
        warnings = emptyList()
      ),
      "renaming the row ID column" to MigratedRows(
        rows = listOf("a:1", "c:3", "d:4"),
        attachments = 4,
        matchesFreshInstall = true,
        warnings = emptyList()
      ),
      "adding a row ID beside a column named rowid" to MigratedRows(
        rows = listOf("a:1:x", "c:3:z", "d:4:w"),
        attachments = 4,
        matchesFreshInstall = true,
        warnings = emptyList()
      )
    )

    assertThat(cases.mapValues { (_, case) -> case.migrate() }).isEqualTo(expected)
  }

  @Test
  fun `copies the old row IDs in the rebuild's row copy`() {
    val statements = mapOf(
      "adding" to (listOf(TEXT_PRIMARY_KEY, TITLE) to listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE)),
      "removing" to (listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE) to listOf(TEXT_PRIMARY_KEY, TITLE)),
      "beside a column named rowid" to (
          listOf(TEXT_PRIMARY_KEY, ROWID_NAMED_COLUMN, TITLE) to
              listOf(TEXT_UNIQUE_ID, ROW_ID, ROWID_NAMED_COLUMN, TITLE)
          )
    ).mapValues { (_, columns) ->
      planSchemaMigration(
        previous = structure(note = columns.first),
        current = structure(note = columns.second)
      ).statements
        .single { it.startsWith("""INSERT INTO "note"""") }
        .substringBeforeLast(" FROM ")
    }

    assertThat(statements).isEqualTo(
      mapOf(
        "adding" to """INSERT INTO "note" ("row_id","id","title") SELECT rowid,"id","title"""",
        "removing" to """INSERT INTO "note" (rowid,"id","title") SELECT "row_id","id","title"""",
        "beside a column named rowid" to
            """INSERT INTO "note" ("row_id","id","rowid","title") SELECT oid,"id","rowid","title""""
      )
    )
  }

  @Test
  fun `a WITHOUT ROWID table that gains a row ID gets new row IDs`() {
    migratedDatabase(
      previous = structure(
        note = listOf(TEXT_PRIMARY_KEY, TITLE),
        noteOptions = " WITHOUT ROWID"
      ),
      current = structure(note = listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE)),
      rows = listOf("""INSERT INTO "note" ("id", "title") VALUES ('a', 'A'), ('c', 'C'), ('d', 'D')""")
    ).use { migrated ->
      assertThat(migrated.strings("""SELECT "id" || ':' || ("row_id" IS NOT NULL) FROM "note" ORDER BY "id""""))
        .containsExactly("a:1", "c:1", "d:1")
        .inOrder()
    }
  }

  @Test
  fun `warns that the row IDs are dropped when a table loses its row ID to WITHOUT ROWID`() {
    val warnings = planSchemaMigration(
      previous = structure(note = listOf(TEXT_UNIQUE_ID, ROW_ID, TITLE)),
      current = structure(
        note = listOf(TEXT_PRIMARY_KEY, TITLE),
        noteOptions = " WITHOUT ROWID"
      )
    ).warnings

    assertThat(warnings).containsExactly(
      "Migration drops column(s) of note with their data: row_id. Edit the migration SQL to keep it."
    )
  }

  private data class RowIdCase(
    val previous: List<ColumnStructure>,
    val current: List<ColumnStructure>,
    val rows: List<String>,
    val read: String
  ) {
    fun migrate(): MigratedRows {
      val previousStructure = structure(note = previous)
      val currentStructure = structure(note = current)
      val freshSchemaRows = BundledSQLiteDriver().open(":memory:").use { fresh ->
        currentStructure.tables.values.forEach { fresh.execSQL(it.schema) }
        fresh.schemaRows()
      }
      return migratedDatabase(
        previous = previousStructure,
        current = currentStructure,
        rows = rows + ATTACHMENT_ROWS
      ).use { migrated ->
        MigratedRows(
          rows = migrated.strings(read),
          attachments = migrated.longs("""SELECT count(*) FROM "attachment"""").single().toInt(),
          matchesFreshInstall = migrated.schemaRows() == freshSchemaRows,
          warnings = planSchemaMigration(
            previous = previousStructure,
            current = currentStructure
          ).warnings
        )
      }
    }
  }

  private data class MigratedRows(
    val rows: List<String>,
    val attachments: Int,
    val matchesFreshInstall: Boolean,
    val warnings: List<String>
  )

  private companion object {
    val TEXT_PRIMARY_KEY = ColumnStructure(
      id = true,
      name = "id",
      sqlType = "TEXT",
      schema = """"id" TEXT NOT NULL PRIMARY KEY""",
      notNull = true
    )
    val TEXT_UNIQUE_ID = TEXT_PRIMARY_KEY.copy(schema = """"id" TEXT NOT NULL UNIQUE""")
    val ROW_ID = ColumnStructure(
      name = "row_id",
      sqlType = "INTEGER",
      schema = """"row_id" INTEGER PRIMARY KEY""",
      rowId = true
    )
    val ROWID_NAMED_COLUMN = ColumnStructure(
      name = "rowid",
      sqlType = "TEXT",
      schema = """"rowid" TEXT"""
    )
    val TITLE = ColumnStructure(
      name = "title",
      sqlType = "TEXT",
      schema = """"title" TEXT NOT NULL DEFAULT ''""",
      notNull = true,
      notNullDefault = "''"
    )
    val ATTACHMENT = listOf(
      ColumnStructure(
        id = true,
        autoIncrement = true,
        name = "id",
        sqlType = "INTEGER",
        schema = """"id" INTEGER PRIMARY KEY AUTOINCREMENT"""
      ),
      ColumnStructure(
        name = "note",
        onDeleteCascade = true,
        sqlType = "TEXT",
        schema = """"note" TEXT NOT NULL REFERENCES "note"("id") ON DELETE CASCADE""",
        notNull = true
      )
    )
    val ATTACHMENT_ROWS = listOf(
      """INSERT INTO "attachment" ("note") VALUES ('a'), ('c'), ('d'), ('d')"""
    )

    fun structure(
      note: List<ColumnStructure>,
      noteOptions: String = ""
    ) = DatabaseStructure(
      tables = mapOf(
        "note" to quotedMigrationTable(
          name = "note",
          columns = note,
          options = noteOptions
        ),
        "attachment" to quotedMigrationTable(
          name = "attachment",
          columns = ATTACHMENT
        )
      )
    )
  }
}
