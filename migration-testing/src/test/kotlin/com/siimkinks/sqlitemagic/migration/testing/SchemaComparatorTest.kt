package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class SchemaComparatorTest {
  @Test
  fun `safe schema formatting matches`() {
    val cases = listOf(
      SchemaCase(
        label = "table quoting whitespace keyword case and comment",
        expected = listOf("CREATE TABLE account (id INTEGER PRIMARY KEY, label TEXT DEFAULT 'Open')"),
        actual = listOf(
          "create table if not exists main.\"account\" (`id` integer primary key," +
              " [label] text /*x*/ default 'Open')"
        )
      ),
      SchemaCase(
        label = "index name table qualifier and quoted indexed identifier",
        expected = listOf("CREATE TABLE t (value TEXT)", "CREATE INDEX ix ON t(value COLLATE NOCASE DESC)"),
        actual = listOf(
          "CREATE TABLE \"t\" ([value] text)",
          "create index if not exists main.ix on t(`value` collate nocase desc)"
        )
      ),
      SchemaCase(
        label = "quoted cast target type in table expression",
        expected = listOf("CREATE TABLE t(value TEXT CHECK(CAST(value AS TEXT) <> ''))"),
        actual = listOf("CREATE TABLE t(value TEXT CHECK(CAST(`value` AS \"TEXT\") <> ''))")
      ),
      SchemaCase(
        label = "punctuation inside quoted identifiers",
        expected = listOf("CREATE TABLE t(\"(\" INT, \"),\" TEXT)"),
        actual = listOf("CREATE TABLE t([(] int, [),] text)")
      )
    )
    cases.forEach { case ->
      assertWithMessage(case.label)
        .that(compare(case))
        .isEmpty()
    }
  }

  @Test
  fun `view differences do not affect table and index validation`() {
    val schema = listOf("CREATE TABLE t(value TEXT)", "CREATE INDEX ix ON t(value)")
    val cases = listOf(
      SchemaCase(
        label = "changed view query and output columns",
        expected = schema + "CREATE VIEW v(x) AS SELECT value FROM t WHERE value = 'Open'",
        actual = schema + "CREATE VIEW v(y) AS SELECT value FROM t WHERE value = 'Closed'"
      ),
      SchemaCase(
        label = "missing view",
        expected = schema + "CREATE VIEW v AS SELECT value FROM t",
        actual = schema
      ),
      SchemaCase(
        label = "additional view",
        expected = schema,
        actual = schema + "CREATE VIEW v AS SELECT value FROM t"
      ),
      SchemaCase(
        label = "values query requires no view parsing",
        expected = schema + "CREATE VIEW v AS VALUES('Open')",
        actual = schema + "CREATE VIEW v AS VALUES('Closed')"
      )
    )
    cases.forEach { case ->
      assertWithMessage(case.label)
        .that(compare(case))
        .isEmpty()
    }
  }

  @Test
  fun `schema semantics differ`() {
    val cases = listOf(
      tableCase(
        label = "declared type and rowid alias",
        expected = "id INTEGER PRIMARY KEY",
        actual = "id INT PRIMARY KEY"
      ),
      tableCase(
        label = "descending primary key rowid exception",
        expected = "id INTEGER PRIMARY KEY",
        actual = "id INTEGER PRIMARY KEY DESC"
      ),
      tableCase(
        label = "autoincrement",
        expected = "id INTEGER PRIMARY KEY",
        actual = "id INTEGER PRIMARY KEY AUTOINCREMENT"
      ),
      tableCase(
        label = "double quoted default literal matching column name",
        expected = "name TEXT DEFAULT \"NAME\"",
        actual = "name TEXT DEFAULT \"name\""
      ),
      tableCase(
        label = "default literal case",
        expected = "value TEXT DEFAULT 'Open'",
        actual = "value TEXT DEFAULT 'open'"
      ),
      tableCase(
        label = "default expression",
        expected = "value INT DEFAULT 0",
        actual = "value INT DEFAULT (1 - 1)"
      ),
      tableCase(
        label = "not null",
        expected = "value TEXT",
        actual = "value TEXT NOT NULL"
      ),
      tableCase(
        label = "unique",
        expected = "value TEXT",
        actual = "value TEXT UNIQUE"
      ),
      tableCase(
        label = "check expression",
        expected = "value INT CHECK(value > 0)",
        actual = "value INT CHECK(value >= 0)"
      ),
      tableCase(
        label = "quoted identifier retains token grouping",
        expected = "a INT,b INT,\"a + b\" INT,CHECK(\"a + b\" > 0)",
        actual = "a INT,b INT,\"a + b\" INT,CHECK(a + b > 0)"
      ),
      SchemaCase(
        label = "DQS check literal is not a column of another table",
        expected = listOf("CREATE TABLE other(ABC TEXT)", "CREATE TABLE t(value TEXT CHECK(value=\"ABC\"))"),
        actual = listOf("CREATE TABLE other(ABC TEXT)", "CREATE TABLE t(value TEXT CHECK(value=\"abc\"))")
      ),
      tableCase(
        label = "column collation",
        expected = "value TEXT COLLATE BINARY",
        actual = "value TEXT COLLATE NOCASE"
      ),
      tableCase(
        label = "column order",
        expected = "a INT, b INT",
        actual = "b INT, a INT"
      ),
      tableCase(
        label = "composite primary key order",
        expected = "a INT, b INT, PRIMARY KEY(a,b)",
        actual = "a INT, b INT, PRIMARY KEY(b,a)"
      ),
      SchemaCase(
        label = "strict table option",
        expected = listOf("CREATE TABLE t(value INT)"),
        actual = listOf("CREATE TABLE t(value INT) STRICT")
      ),
      SchemaCase(
        label = "without rowid table option",
        expected = listOf("CREATE TABLE t(value INT PRIMARY KEY)"),
        actual = listOf("CREATE TABLE t(value INT PRIMARY KEY) WITHOUT ROWID")
      ),
      SchemaCase(
        label = "foreign key deferral",
        expected = listOf(
          "CREATE TABLE parent(id INT PRIMARY KEY)",
          "CREATE TABLE t(value INT REFERENCES parent(id) DEFERRABLE INITIALLY DEFERRED)"
        ),
        actual = listOf(
          "CREATE TABLE parent(id INT PRIMARY KEY)",
          "CREATE TABLE t(value INT REFERENCES parent(id))"
        )
      ),
      SchemaCase(
        label = "composite foreign key order",
        expected = listOf(
          "CREATE TABLE p(a INT,b INT,PRIMARY KEY(a,b))",
          "CREATE TABLE t(a INT,b INT,FOREIGN KEY(a,b) REFERENCES p(a,b))"
        ),
        actual = listOf(
          "CREATE TABLE p(a INT,b INT,PRIMARY KEY(a,b))",
          "CREATE TABLE t(a INT,b INT,FOREIGN KEY(b,a) REFERENCES p(a,b))"
        )
      ),
      indexCase(
        label = "index collation",
        expected = "value COLLATE BINARY",
        actual = "value COLLATE NOCASE"
      ),
      indexCase(
        label = "index descending",
        expected = "value ASC",
        actual = "value DESC"
      ),
      indexCase(
        label = "index expression",
        expected = "length(value)",
        actual = "length(value)+1"
      ),
      SchemaCase(
        label = "partial index predicate literal",
        expected = listOf("CREATE TABLE t(value TEXT)", "CREATE INDEX ix ON t(value) WHERE value = 'Open'"),
        actual = listOf("CREATE TABLE t(value TEXT)", "CREATE INDEX ix ON t(value) WHERE value = 'open'")
      ),
      tableCase(
        label = "generated expression",
        expected = "a INT,b INT AS(a+1)",
        actual = "a INT,b INT AS(a+2)"
      )
    )
    cases.forEach { case ->
      assertWithMessage(case.label)
        .that(compare(case))
        .isNotEmpty()
    }
  }

  @Test
  fun `differences identify affected columns deterministically`() {
    val differences = compare(
      tableCase(
        label = "column",
        expected = "value INTEGER",
        actual = "value TEXT"
      )
    )
    assertWithMessage("column diff path")
      .that(differences.map(SchemaDifference::path))
      .containsExactly("table[t].column[value]", "table[t].constraints")
      .inOrder()
  }

  @Test
  fun `sqlite automatic index names do not enter schema model`() {
    val schema = inspect(listOf("CREATE TABLE t(value TEXT UNIQUE)"))
    assertWithMessage("automatic index")
      .that(schema.tables.getValue("t").indices.single().name)
      .isNull()
    assertWithMessage("uniqueness retained")
      .that(schema.tables.getValue("t").indices.single().unique)
      .isTrue()
  }

  @Test(expected = SchemaInspectionException::class)
  fun `unsupported virtual tables fail inspection`() {
    inspect(listOf("CREATE VIRTUAL TABLE t USING fts5(value)"))
  }

  private fun compare(case: SchemaCase) = SchemaComparator.compare(
    expected = inspect(case.expected),
    actual = inspect(case.actual)
  )

  private fun inspect(sql: List<String>) = BundledSQLiteDriver()
    .open(":memory:")
    .use { connection ->
      sql.forEach { statement ->
        SQLiteQueries.execute(
          connection = connection,
          sql = statement
        )
      }
      SchemaIntrospector.inspect(connection)
    }

  private fun tableCase(
    label: String,
    expected: String,
    actual: String
  ) = SchemaCase(
    label = label,
    expected = listOf("CREATE TABLE t($expected)"),
    actual = listOf("CREATE TABLE t($actual)")
  )

  private fun indexCase(
    label: String,
    expected: String,
    actual: String
  ) = SchemaCase(
    label = label,
    expected = listOf("CREATE TABLE t(value TEXT)", "CREATE INDEX ix ON t($expected)"),
    actual = listOf("CREATE TABLE t(value TEXT)", "CREATE INDEX ix ON t($actual)")
  )

  private data class SchemaCase(
    val label: String,
    val expected: List<String>,
    val actual: List<String>
  )
}
