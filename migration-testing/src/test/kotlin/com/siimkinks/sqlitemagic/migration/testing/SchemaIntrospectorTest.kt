package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

class SchemaIntrospectorTest {
  @Test
  fun `foreign key columns retain sequence actions and implicit primary key targets`() {
    val schema = inspect(
      listOf(
        "CREATE TABLE PÄrent(A INT, B INT, PRIMARY KEY(A, B))",
        "CREATE TABLE Other(ID INTEGER PRIMARY KEY)",
        "CREATE TABLE Child(X INT, Y INT, Z INT REFERENCES Other," +
            " FOREIGN KEY(X, Y) REFERENCES PÄrent(A, B) ON UPDATE CASCADE ON DELETE SET NULL)"
      )
    )
    val expected = listOf(
      listOf(
        InspectedForeignKeyColumn(
          sequence = 0L,
          targetTable = "other",
          sourceColumn = "z",
          targetColumn = null,
          onUpdate = "no action",
          onDelete = "no action",
          match = "none"
        )
      ),
      listOf(
        InspectedForeignKeyColumn(
          sequence = 0L,
          targetTable = "pÄrent",
          sourceColumn = "x",
          targetColumn = "a",
          onUpdate = "cascade",
          onDelete = "set null",
          match = "none"
        ),
        InspectedForeignKeyColumn(
          sequence = 1L,
          targetTable = "pÄrent",
          sourceColumn = "y",
          targetColumn = "b",
          onUpdate = "cascade",
          onDelete = "set null",
          match = "none"
        )
      )
    )
    assertThat(schema.tables.getValue("child").foreignKeys)
      .isEqualTo(expected)
  }

  @Test
  fun `index columns retain expression rowid collation and key metadata`() {
    val schema = inspect(
      listOf(
        "CREATE TABLE T(ÄValue TEXT)",
        "CREATE INDEX IX ON T(length(ÄValue) DESC, ÄValue COLLATE NOCASE ASC)"
      )
    )
    val expected = listOf(
      InspectedIndexColumn(
        sequence = 0L,
        columnId = -2L,
        name = null,
        descending = 1L,
        collation = "binary",
        key = 1L
      ),
      InspectedIndexColumn(
        sequence = 1L,
        columnId = 0L,
        name = "Ävalue",
        descending = 0L,
        collation = "nocase",
        key = 1L
      ),
      InspectedIndexColumn(
        sequence = 2L,
        columnId = -1L,
        name = null,
        descending = 0L,
        collation = "binary",
        key = 0L
      )
    )
    assertThat(schema.tables.getValue("t").indices.single().columns)
      .isEqualTo(expected)
  }

  @Test
  fun `foreign key declaration order and ASCII case do not change inspected groups`() {
    val expected = inspect(
      listOf(
        "CREATE TABLE P(A INT, B INT, PRIMARY KEY(A, B))",
        "CREATE TABLE T(X INT, Y INT, Z INT," +
            " FOREIGN KEY(X,Y) REFERENCES P(A,B) ON DELETE CASCADE," +
            " FOREIGN KEY(Z) REFERENCES P(A) ON UPDATE RESTRICT)"
      )
    )
    val actual = inspect(
      listOf(
        "CREATE TABLE p(a INT, b INT, PRIMARY KEY(a, b))",
        "CREATE TABLE t(x INT, y INT, z INT," +
            " FOREIGN KEY(z) REFERENCES p(a) ON UPDATE RESTRICT," +
            " FOREIGN KEY(x,y) REFERENCES p(a,b) ON DELETE CASCADE)"
      )
    )
    assertThat(actual.tables.getValue("t").foreignKeys)
      .isEqualTo(expected.tables.getValue("t").foreignKeys)
  }

  @Test
  fun `foreign key action and target changes remain distinguishable`() {
    val prefix = listOf("CREATE TABLE p(a INT, b INT, PRIMARY KEY(a, b))")
    val expected = inspect(prefix + "CREATE TABLE t(x INT REFERENCES p(a))")
    val cases = mapOf(
      "delete action" to "x INT REFERENCES p(a) ON DELETE CASCADE",
      "update action" to "x INT REFERENCES p(a) ON UPDATE RESTRICT",
      "explicit target" to "x INT REFERENCES p(b)",
      "implicit target" to "x INT REFERENCES p"
    )
    cases.forEach { (label, columns) ->
      val actual = inspect(prefix + "CREATE TABLE t($columns)")
      assertWithMessage(label)
        .that(
          SchemaComparator.compare(
            expected = expected,
            actual = actual
          )
            .map(SchemaDifference::path)
        )
        .contains("table[t].foreignKeys")
    }
  }

  @Test
  fun `index term order and metadata changes remain distinguishable`() {
    val table = "CREATE TABLE t(a TEXT, b TEXT)"
    val expected = inspect(listOf(table, "CREATE INDEX ix ON t(a COLLATE NOCASE DESC, b)"))
    val cases = mapOf(
      "term order" to "b, a COLLATE NOCASE DESC",
      "descending" to "a COLLATE NOCASE ASC, b",
      "collation" to "a COLLATE BINARY DESC, b",
      "expression" to "length(a) COLLATE NOCASE DESC, b"
    )
    cases.forEach { (label, terms) ->
      val actual = inspect(listOf(table, "CREATE INDEX ix ON t($terms)"))
      assertWithMessage(label)
        .that(actual.tables.getValue("t").indices.single().columns)
        .isNotEqualTo(expected.tables.getValue("t").indices.single().columns)
    }
  }

  @Test
  fun `foreign key and index metadata reject missing or incorrectly typed fields`() {
    val schema = listOf(
      "CREATE TABLE p(id INTEGER PRIMARY KEY)",
      "CREATE TABLE t(value INT REFERENCES p(id))",
      "CREATE INDEX ix ON t(value)"
    )
    val cases = listOf(
      MetadataCase(
        pragma = "PRAGMA main.foreign_key_list(\"t\")",
        fields = mapOf(
          "id" to "0",
          "seq" to "0",
          "table" to "'p'",
          "from" to "'value'",
          "to" to "'id'",
          "on_update" to "'NO ACTION'",
          "on_delete" to "'NO ACTION'",
          "match" to "'NONE'"
        )
      ),
      MetadataCase(
        pragma = "PRAGMA main.index_xinfo(\"ix\")",
        fields = mapOf(
          "seqno" to "0",
          "cid" to "0",
          "name" to "'value'",
          "desc" to "0",
          "coll" to "'BINARY'",
          "key" to "1"
        )
      )
    )
    cases.forEach { case ->
      case.fields.forEach { (field, value) ->
        val invalidRows = mapOf(
          "missing" to case.fields.filterKeys { it != field },
          "wrong type" to (case.fields + (field to when {
            value.startsWith("'") -> "42"
            else -> "'invalid'"
          }))
        )
        invalidRows.forEach { (label, fields) ->
          val query = "SELECT " + fields.entries.joinToString { (name, expression) ->
            "$expression AS \"$name\""
          }
          assertThrows("${case.pragma}: $field $label", SchemaInspectionException::class.java) {
            inspect(
              sql = schema,
              queryOverrides = mapOf(case.pragma to query)
            )
          }
        }
      }
    }
  }

  private fun inspect(
    sql: List<String>,
    queryOverrides: Map<String, String> = emptyMap()
  ) = BundledSQLiteDriver()
    .open(":memory:")
    .use { connection ->
      sql.forEach { statement ->
        SQLiteQueries.execute(
          connection = connection,
          sql = statement
        )
      }
      SchemaIntrospector.inspect(
        object : SQLiteConnection by connection {
          override fun prepare(sql: String): SQLiteStatement = connection.prepare(queryOverrides[sql] ?: sql)
        }
      )
    }

  private data class MetadataCase(
    val pragma: String,
    val fields: Map<String, String>
  )
}
