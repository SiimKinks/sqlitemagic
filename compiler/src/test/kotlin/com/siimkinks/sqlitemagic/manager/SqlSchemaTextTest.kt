package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

internal class SqlSchemaTextTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("normalizeSqlCases")
  fun `normalizes schema SQL`(
    label: String,
    schema: String,
    ownTableName: String?,
    renames: Map<String, String>,
    expected: String
  ) {
    assertThat(
      normalizeSql(
        schema = schema,
        ownTableName = ownTableName,
        renames = renames
      )
    ).isEqualTo(expected)
  }

  @ParameterizedTest(name = "quoted parenthesis {0}")
  @ValueSource(strings = ["'", "\"", "\u0060", "["])
  fun `replaces only the outer table column list`(opening: String) {
    val closing = when (opening) {
      "[" -> "]"
      else -> opening
    }
    val quotedParenthesis = "$opening($closing"
    val schema = "CREATE TABLE books (value TEXT DEFAULT $quotedParenthesis, CHECK (value <> 1)) WITHOUT ROWID"

    assertThat(schema.withoutTableColumns())
      .isEqualTo("CREATE TABLE books (__COLUMNS__) WITHOUT ROWID")
  }

  @ParameterizedTest(name = "quoted table name {0}")
  @ValueSource(strings = ["\"", "\u0060", "["])
  fun `ignores parentheses in a quoted table name`(opening: String) {
    val closing = when (opening) {
      "[" -> "]"
      else -> opening
    }
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
      schema = """CREATE TABLE child (parent_id INTEGER REFERENCES """"
    )

    assertThat(table.normalizedReferencedTableNames()).isEmpty()
  }

  companion object {
    @JvmStatic
    fun normalizeSqlCases() = listOf(
      Arguments.of(
        "normalizes identifiers and whitespace while preserving literal contents",
        "CREATE   TABLE   current   (value TEXT DEFAULT 'current  value')",
        null,
        mapOf("current" to "legacy"),
        "CREATE TABLE legacy (value TEXT DEFAULT 'current  value')"
      ),
      Arguments.of(
        "applies a rename chain in map iteration order",
        "CREATE TABLE current (value TEXT)",
        null,
        mapOf(
          "current" to "middle",
          "middle" to "legacy"
        ),
        "CREATE TABLE legacy (value TEXT)"
      ),
      Arguments.of(
        "applies only the later link of a rename chain listed in reverse order",
        "CREATE TABLE current (value TEXT)",
        null,
        mapOf(
          "middle" to "legacy",
          "current" to "middle"
        ),
        "CREATE TABLE middle (value TEXT)"
      ),
      Arguments.of(
        "does not replace an overlapping identifier within a larger identifier",
        "CREATE TABLE new_table (new TEXT)",
        null,
        mapOf(
          "new" to "old",
          "new_table" to "renamed"
        ),
        "CREATE TABLE renamed (old TEXT)"
      ),
      Arguments.of(
        "replaces the own table before applying a matching rename",
        "CREATE TABLE current (parent TEXT REFERENCES current(id) DEFAULT 'current  value')",
        "current",
        mapOf("current" to "legacy"),
        "CREATE TABLE __TABLE__ (parent TEXT REFERENCES __TABLE__(id) DEFAULT 'current  value')"
      ),
      Arguments.of(
        "preserves quoted identifiers while replacing matching unquoted identifiers",
        """CREATE TABLE "current" (value TEXT COLLATE "current value" DEFAULT 'current  value')""",
        null,
        mapOf("current" to "legacy"),
        """CREATE TABLE legacy (value TEXT COLLATE "current value" DEFAULT 'current  value')"""
      ),
      Arguments.of(
        "keeps sequential replacement behavior for non-simple names",
        "CREATE TABLE current (value TEXT)",
        null,
        mapOf("current" to "legacy name"),
        "CREATE TABLE legacy name (value TEXT)"
      ),
      Arguments.of(
        "treats dollar signs as part of unquoted identifiers",
        $$"CREATE TABLE foo$bar (value TEXT)",
        null,
        mapOf("foo" to "legacy"),
        $$"CREATE TABLE foo$bar (value TEXT)"
      ),
      Arguments.of(
        "preserves an unclosed quoted identifier during normalization",
        """CREATE TABLE "current""",
        null,
        mapOf("curren" to "legacy"),
        """CREATE TABLE "current"""
      )
    )
  }
}
