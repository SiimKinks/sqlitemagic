package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class SqlSchemaTextTest {
  //region normalizeSql
  @Test
  fun `normalizes identifiers and whitespace while preserving literal contents`() {
    assertThat(
      normalizeSql(
        schema = "CREATE   TABLE   current   (value TEXT DEFAULT 'current  value')",
        ownTableName = null,
        renames = mapOf("current" to "legacy")
      )
    ).isEqualTo("CREATE TABLE legacy (value TEXT DEFAULT 'current  value')")
  }

  @Test
  fun `applies rename chains in map iteration order`() {
    assertThat(
      normalizeSql(
        schema = "CREATE TABLE current (value TEXT)",
        ownTableName = null,
        renames = linkedMapOf(
          "current" to "middle",
          "middle" to "legacy"
        )
      )
    ).isEqualTo("CREATE TABLE legacy (value TEXT)")

    assertThat(
      normalizeSql(
        schema = "CREATE TABLE current (value TEXT)",
        ownTableName = null,
        renames = linkedMapOf(
          "middle" to "legacy",
          "current" to "middle"
        )
      )
    ).isEqualTo("CREATE TABLE middle (value TEXT)")
  }

  @Test
  fun `does not replace an overlapping identifier within a larger identifier`() {
    assertThat(
      normalizeSql(
        schema = "CREATE TABLE new_table (new TEXT)",
        ownTableName = null,
        renames = linkedMapOf(
          "new" to "old",
          "new_table" to "renamed"
        )
      )
    ).isEqualTo("CREATE TABLE renamed (old TEXT)")
  }

  @Test
  fun `replaces the own table before applying a matching rename`() {
    assertThat(
      normalizeSql(
        schema = "CREATE TABLE current (parent TEXT REFERENCES current(id) DEFAULT 'current  value')",
        ownTableName = "current",
        renames = mapOf("current" to "legacy")
      )
    ).isEqualTo("CREATE TABLE __TABLE__ (parent TEXT REFERENCES __TABLE__(id) DEFAULT 'current  value')")
  }

  @Test
  fun `preserves quoted identifiers while replacing matching unquoted identifiers`() {
    assertThat(
      normalizeSql(
        schema = "CREATE TABLE \"current\" (value TEXT COLLATE \"current value\" DEFAULT 'current  value')",
        ownTableName = null,
        renames = mapOf("current" to "legacy")
      )
    ).isEqualTo("CREATE TABLE legacy (value TEXT COLLATE \"current value\" DEFAULT 'current  value')")
  }

  @Test
  fun `keeps sequential replacement behavior for non-simple names`() {
    assertThat(
      normalizeSql(
        schema = "CREATE TABLE current (value TEXT)",
        ownTableName = null,
        renames = mapOf("current" to "legacy name")
      )
    ).isEqualTo("CREATE TABLE legacy name (value TEXT)")
  }

  @Test
  fun `treats dollar signs as part of unquoted identifiers`() {
    assertThat(
      normalizeSql(
        schema = $$"CREATE TABLE foo$bar (value TEXT)",
        ownTableName = null,
        renames = mapOf("foo" to "legacy")
      )
    ).isEqualTo($$"CREATE TABLE foo$bar (value TEXT)")
  }

  @Test
  fun `preserves an unclosed quoted identifier during normalization`() {
    assertThat(
      normalizeSql(
        schema = "CREATE TABLE \"current",
        ownTableName = null,
        renames = mapOf("curren" to "legacy")
      )
    ).isEqualTo("CREATE TABLE \"current")
  }
  //endregion

  //region withoutTableColumns
  @ParameterizedTest(name = "quoted parenthesis {0}")
  @ValueSource(strings = ["'", "\"", "\u0060", "["])
  fun `replaces only the outer table column list`(opening: String) {
    val closing = if (opening == "[") "]" else opening
    val quotedParenthesis = "$opening($closing"
    val schema = "CREATE TABLE books (value TEXT DEFAULT $quotedParenthesis, CHECK (value <> 1)) WITHOUT ROWID"

    assertThat(schema.withoutTableColumns())
      .isEqualTo("CREATE TABLE books (__COLUMNS__) WITHOUT ROWID")
  }

  @ParameterizedTest(name = "quoted table name {0}")
  @ValueSource(strings = ["\"", "\u0060", "["])
  fun `ignores parentheses in a quoted table name`(opening: String) {
    val closing = if (opening == "[") "]" else opening
    val quotedTableName = opening + "books(" + closing
    val schema = "CREATE TABLE $quotedTableName (value TEXT)"

    assertThat(schema.withoutTableColumns())
      .isEqualTo("CREATE TABLE $quotedTableName (__COLUMNS__)")
  }

  @Test
  fun `leaves schemas without a balanced table column list unchanged`() {
    listOf(
      "CREATE TABLE books",
      "CREATE TABLE books (value TEXT"
    ).forEach { schema ->
      assertWithMessage(schema)
        .that(schema.withoutTableColumns())
        .isEqualTo(schema)
    }
  }
  //endregion

  //region rebuild predicates
  @Test
  fun `requires a value for an id column in a without rowid table`() {
    val table = TableStructure(
      schema = "CREATE TABLE items (id TEXT) without rowid"
    )

    assertThat(
      ColumnStructure(id = true)
        .requiresValueDuringRebuild(table = table)
    ).isTrue()
  }

  @Test
  fun `does not require a value for non-id columns or ordinary tables`() {
    val withoutRowIdTable = TableStructure(
      schema = "CREATE TABLE items (value TEXT) WITHOUT ROWID"
    )
    val ordinaryTable = TableStructure(
      schema = "CREATE TABLE items (id TEXT)"
    )

    assertThat(
      ColumnStructure()
        .requiresValueDuringRebuild(table = withoutRowIdTable)
    ).isFalse()
    assertThat(
      ColumnStructure(id = true)
        .requiresValueDuringRebuild(table = ordinaryTable)
    ).isFalse()
  }

  @Test
  fun `ignores without rowid text inside quoted schema segments`() {
    val table = TableStructure(
      schema = "CREATE TABLE items (value TEXT DEFAULT 'WITHOUT ROWID')"
    )

    assertThat(
      ColumnStructure(id = true)
        .requiresValueDuringRebuild(table = table)
    ).isFalse()
  }

  @Test
  fun `allows a regular column with a constant default to be added`() {
    assertThat(
      ColumnStructure(schema = "label TEXT DEFAULT ''")
        .canBeAddedWithAlterTable()
    ).isTrue()
  }

  @Test
  fun `rejects columns with structural flags that require a rebuild`() {
    listOf(
      "id" to ColumnStructure(id = true),
      "auto-increment" to ColumnStructure(autoIncrement = true),
      "delete cascade" to ColumnStructure(onDeleteCascade = true)
    ).forEach { (label, column) ->
      assertWithMessage(label)
        .that(column.canBeAddedWithAlterTable())
        .isFalse()
    }
  }

  @Test
  fun `rejects columns with unsupported constraints and defaults`() {
    listOf(
      "primary key" to ColumnStructure(schema = "value INTEGER PRIMARY KEY"),
      "unique" to ColumnStructure(schema = "value TEXT UNIQUE"),
      "references" to ColumnStructure(schema = "value INTEGER REFERENCES parents(id)"),
      "current time" to ColumnStructure(schema = "value TEXT DEFAULT CURRENT_TIME"),
      "current date" to ColumnStructure(schema = "value TEXT DEFAULT current_date"),
      "current timestamp" to ColumnStructure(schema = "value TEXT DEFAULT CURRENT_TIMESTAMP"),
      "parenthesized default" to ColumnStructure(schema = "value INTEGER DEFAULT (1)")
    ).forEach { (label, column) ->
      assertWithMessage(label)
        .that(column.canBeAddedWithAlterTable())
        .isFalse()
    }
  }

  @Test
  fun `ignores unsupported constraint text inside quoted schema segments`() {
    assertThat(
      ColumnStructure(
        schema = "value TEXT DEFAULT 'PRIMARY KEY UNIQUE REFERENCES CURRENT_TIMESTAMP (1)'"
      ).canBeAddedWithAlterTable()
    ).isTrue()
  }
  //endregion

  //region normalizedReferencedTableNames
  @Test
  fun `extracts case-insensitive references in every supported identifier quote form`() {
    val table = TableStructure(
      schema = """
        CREATE TABLE child (
          parent_id INTEGER REFERENCES Parents(id),
          audit_id INTEGER REFERENCES "Audit Log" (id),
          archive_id INTEGER REFERENCES [Archive] (id),
          event_id INTEGER REFERENCES BACKTICKEventsBACKTICK(id)
        )
      """
        .trimIndent()
        .replace(
          oldValue = "BACKTICK",
          newValue = "\u0060"
        )
    )

    assertThat(table.normalizedReferencedTableNames())
      .containsExactly("parents", "audit log", "archive", "events")
      .inOrder()
  }

  @Test
  fun `ignores references inside quoted text and longer keywords`() {
    val table = TableStructure(
      schema = """
        CREATE TABLE child (
          literal TEXT DEFAULT 'REFERENCES fake(id)',
          quoted TEXT COLLATE "REFERENCES quoted(id)",
          prefix TEXT XREFERENCES prefixed(id),
          suffix TEXT REFERENCESignored(ignored_id),
          parent_id INTEGER REFERENCES
            real_parent (id)
        )
      """.trimIndent()
    )

    assertThat(table.normalizedReferencedTableNames())
      .containsExactly("real_parent")
  }

  @Test
  fun `ignores an unclosed quoted referenced table name`() {
    val table = TableStructure(
      schema = "CREATE TABLE child (parent_id INTEGER REFERENCES \""
    )

    assertThat(table.normalizedReferencedTableNames()).isEmpty()
  }
  //endregion

  //region normalizedSqlIdentifier
  @Test
  fun `normalizes SQL identifiers to lower case`() {
    assertThat("Books_TABLE".normalizedSqlIdentifier())
      .isEqualTo("books_table")
  }
  //endregion
}
