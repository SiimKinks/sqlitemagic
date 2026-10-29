package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class RawQueryTest {
  private data class MinorName(
    val id: Long,
    val name: String?
  )

  private data class PersonId(val value: Long)

  @Test
  fun `raw queries bind native arguments and read columns by position`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val minors = SELECT
        .RAW("SELECT id, name FROM person WHERE age < ? ORDER BY id", 18)
        .readAs(LONG, PERSON.NAME)
        .usingConnection(connection)
      val compiled = minors.compile()
      val first = minors.takeFirst()
      val minorRows = listOf(
        Row2(3L, null),
        Row2(4L, "Linus")
      )

      assertThat(
        listOf(
          compiled.sql,
          compiled.plan.prepared.arguments(emptyList()),
          compiled.execute(),
          compiled.execute(),
          minors
            .mapTo(::MinorName)
            .execute(),
          first.sql,
          first.execute(),
          (SELECT RAW "SELECT count(*) FROM person")
            .readAs(LONG)
            .usingConnection(connection)
            .execute()
        )
      ).isEqualTo(
        listOf(
          "SELECT id, name FROM person WHERE age < ? ORDER BY id",
          listOf(NativeValue.Integer(18)),
          minorRows,
          minorRows,
          listOf(
            MinorName(
              id = 3L,
              name = null
            ),
            MinorName(
              id = 4L,
              name = "Linus"
            )
          ),
          "SELECT id, name FROM person WHERE age < ? ORDER BY id",
          minorRows.first(),
          listOf(4L)
        )
      )
    }
  }

  @Test
  fun `single columns map each row for every terminal`() {
    val adultIds = (SELECT RAW "SELECT id FROM person WHERE age >= 18 ORDER BY id")
      .readAs(LONG)
      .mapTo(::PersonId)

    personDatabase().use { database ->
      val connected = adultIds.usingConnection(database.dbConnection())
      assertThat(
        listOf(
          connected.execute(),
          connected
            .takeFirst()
            .execute()
        )
      ).isEqualTo(
        listOf(
          listOf(PersonId(1L), PersonId(2L)),
          PersonId(1L)
        )
      )
    }
  }

  @Test
  fun `native arguments bind their storage class and NaN rejects`() {
    personDatabase().use { database ->
      val types = SELECT
        .RAW(
          "SELECT typeof(?), typeof(?), typeof(?), typeof(?), typeof(?), typeof(?)",
          9_007_199_254_740_993L,
          1.5,
          "10",
          byteArrayOf(1),
          null,
          true
        )
        .readAs(STRING, STRING, STRING, STRING, STRING, STRING)
        .usingConnection(database.dbConnection())
        .execute()

      assertThat(types).isEqualTo(
        listOf(Row6("integer", "real", "text", "blob", "null", "integer"))
      )
      val failure = assertThrows(IllegalArgumentException::class.java) {
        SELECT.RAW("SELECT ?", Double.NaN)
      }
      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("NaN cannot be encoded as a required SQL REAL value")
    }
  }

  @Test
  fun `descriptors and field templates keep their null policies`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val unnamed = SELECT.RAW("SELECT name, token FROM person WHERE id = 3")

      assertThat(
        listOf(
          unnamed
            .readAs(STRING.nullable, PERSON.TOKEN)
            .usingConnection(connection)
            .execute(),
          unnamed
            .readAs(PERSON.NAME, STRING)
            .usingConnection(connection)
            .execute()
        )
      ).isEqualTo(
        listOf(
          listOf(Row2(null, Token("n"))),
          listOf(Row2(null, "db:n"))
        )
      )
      assertThat(
        sqlExceptionMessage {
          unnamed
            .readAs(STRING, STRING)
            .usingConnection(connection)
            .execute()
        }
      ).isEqualTo("Required SQL result returned SQL NULL")
    }
  }

  @Test
  fun `every execution checks once that the result has the columns readAs reads`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val wide = SELECT
        .RAW("SELECT id, name, age FROM person")
        .readAs(LONG, STRING.nullable)
        .usingConnection(connection)
      val narrow = SELECT
        .RAW("SELECT id FROM person WHERE id < 0")
        .readAs(LONG, LONG)
        .usingConnection(connection)
      val failures = listOf(
        wide::execute,
        wide.takeFirst()::execute,
        narrow.takeFirst()::execute,
        {
          wide
            .compile()
            .plan
            .observation()
            .query
            .runBlocking()
        }
      ).map { execution ->
        assertThrows(IllegalStateException::class.java) { execution() }.message
      }

      assertThat(failures).isEqualTo(
        listOf(
          "raw query returned 3 columns; readAs reads 2",
          "raw query returned 3 columns; readAs reads 2",
          "raw query returned 1 columns; readAs reads 2",
          "raw query returned 3 columns; readAs reads 2"
        )
      )
    }
  }

  @Test
  fun `cursors belong to their callers and read cursors read their current row`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val unread = SELECT
        .RAW("SELECT id, name FROM person WHERE id > ? ORDER BY id", 2L)
        .usingConnection(connection)
        .toCursor()
      val read = SELECT
        .RAW("SELECT id, name FROM person WHERE id > ? ORDER BY id", 2L)
        .readAs(LONG, PERSON.NAME)
        .mapTo(::MinorName)
        .usingConnection(connection)
        .toCursor()
      unread.execute().use { unreadCursor ->
        read.execute().use { readCursor ->
          val rows = buildList {
            while (unreadCursor.moveToNext() && readCursor.moveToNext()) {
              add(unreadCursor.getLong(0) to read.getFromCurrentPosition(readCursor))
            }
          }

          assertThat(listOf(unreadCursor.isClosed, readCursor.isClosed)).isEqualTo(listOf(false, false))
          assertThat(rows).isEqualTo(
            listOf(
              3L to MinorName(
                id = 3L,
                name = null
              ),
              4L to MinorName(
                id = 4L,
                name = "Linus"
              )
            )
          )
        }
      }
    }
  }

  @Test
  fun `declarations name their sources once while undeclared queries have unknown sources`() {
    fun RawSelect<*>.observedTables() = toCursor().plan.prepared.dependencies.observedTables.asList()
    fun declared(table: Table<*>, vararg tables: Table<*>) = dependencySnapshot(
      QueryDependencies.declared(
        table = table,
        tables = tables
      )
    )

    val books = testTable<Any>(name = "Books")
    val left = testTable<Any>(
      name = "Books",
      alias = "left"
    )
    val right = testTable<Any>(
      name = "bOOKS",
      alias = "right"
    )
    val temporary = testTable<Any>(
      name = "items",
      temporary = true
    )
    val main = testTable<Any>(name = "items")
    val raw = SELECT RAW "SELECT 1"

    assertThat(
      listOf(
        raw.observedTables(),
        raw
          .observes(left, books, left, right)
          .observedTables(),
        raw
          .observes(books)
          .observes(main)
          .observedTables()
      )
    ).isEqualTo(
      listOf(
        emptyList(),
        listOf("Books", "bOOKS"),
        listOf("items")
      )
    )
    assertThat(
      listOf(
        declared(left, books, left, right),
        declared(temporary, main),
        declared(AUTHOR_SUMMARY)
      )
    ).isEqualTo(
      listOf(
        DependencySnapshot(
          direct = listOf(
            SqliteQuerySource(
              schema = MAIN,
              name = "Books"
            )
          ),
          observed = listOf("Books", "bOOKS")
        ),
        DependencySnapshot(
          direct = listOf(
            SqliteQuerySource(
              schema = TEMPORARY,
              name = "items"
            ),
            SqliteQuerySource(
              schema = MAIN,
              name = "items"
            )
          ),
          observed = listOf("items")
        ),
        DependencySnapshot(
          direct = listOf(
            SqliteQuerySource(
              schema = MAIN,
              name = "author_summary",
              kind = VIEW
            )
          ),
          observed = listOf("author")
        )
      )
    )
  }

  @Test
  fun `raw statements bind native arguments and notify only their declared tables`() {
    data class Outcome(
      val sql: String,
      val arguments: List<NativeValue>,
      val affected: Int,
      val rows: List<Person>,
      val triggers: List<Set<String>>
    )

    fun outcome(statement: RawMutationQuery) = petDatabase().use { database ->
      val connection = database.dbConnection()
      val recorder = connection.recordTriggers()
      val compiled = statement
        .usingConnection(connection)
        .compile()
      Outcome(
        sql = compiled.sql,
        arguments = compiled.arguments,
        affected = compiled.execute(),
        rows = connection.persons(),
        triggers = recorder.triggers
      )
    }

    val cases = listOf(
      "undeclared UPDATE" to UPDATE.RAW("UPDATE person SET name = ? WHERE age < ?", "Minor", 18),
      "declared DELETE" to DELETE
        .RAW("DELETE FROM person WHERE id = ?", 3L)
        .notifies(PERSON, PET),
      "replaced declaration" to (DELETE RAW "DELETE FROM person WHERE id = 3")
        .notifies(PET)
        .notifies(PERSON)
    )
    val outcomes = cases.map { (label, statement) -> label to outcome(statement) }

    assertThat(outcomes).isEqualTo(
      listOf(
        "undeclared UPDATE" to Outcome(
          sql = "UPDATE person SET name = ? WHERE age < ?",
          arguments = listOf(NativeValue.Text("Minor"), NativeValue.Integer(18)),
          affected = 2,
          rows = listOf(
            ada,
            grace,
            unnamed.copy(name = "Minor"),
            linus.copy(name = "Minor")
          ),
          triggers = emptyList()
        ),
        "declared DELETE" to Outcome(
          sql = "DELETE FROM person WHERE id = ?",
          arguments = listOf(NativeValue.Integer(3)),
          affected = 1,
          rows = listOf(ada, grace, linus),
          triggers = listOf(setOf("person", "pet"))
        ),
        "replaced declaration" to Outcome(
          sql = "DELETE FROM person WHERE id = 3",
          arguments = emptyList(),
          affected = 1,
          rows = listOf(ada, grace, linus),
          triggers = listOf(setOf("person"))
        )
      )
    )
  }

  @Test
  fun `a raw argument in a view definition fails when SQLite creates the view`() {
    val view = GeneratedView(
      viewName = "minor_name",
      temporary = false,
      query = { SELECT COLUMN PERSON.NAME FROM PERSON WHERE "${PERSON.AGE} < ?".expr(BOOLEAN, 18) },
      columns = listOf("person.name")
    )

    assertThat(view.definition.sql).isEqualTo(
      """SELECT "person"."name" AS "person.name" FROM "main"."person" WHERE "person"."age" < ?"""
    )
    personDatabase().use { database ->
      assertThat(sqliteErrorMessage { database.createViews(view) }).contains("parameters are not allowed in views")
    }
  }
}
