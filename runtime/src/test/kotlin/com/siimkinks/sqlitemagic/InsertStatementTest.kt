package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

internal class InsertStatementTest {
  private data class Outcome(
    val affected: Int?,
    val rows: List<Person>,
    val triggers: List<Set<String>>
  )

  private fun outcome(
    query: MutationQuery,
    database: TestDatabase = personDatabase()
  ) = database.use {
    val connection = database.connection
    val recorder = connection.recordTriggers()
    val affected = runCatching {
      query
        .usingConnection(connection)
        .execute()
    }
    Outcome(
      affected = affected.getOrNull(),
      rows = connection.persons(),
      triggers = recorder.triggers
    )
  }

  private fun defaulted(
    id: Long,
    name: String? = "Anon",
    age: Int = 7,
    token: Token = Token("d")
  ) = Person(
    id = id,
    name = name,
    age = age,
    parentId = null,
    token = token,
    flag = true
  )

  @Test
  fun `typed rows render one VALUES clause and insert into the listed columns`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val plan: PlanSnapshot,
      val rows: List<Person>
    )

    listOf(
      Case(
        label = "infix host value",
        query = INSERT INTO PERSON COLUMNS PERSON.TOKEN VALUES Token("x"),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("token") VALUES (?)""",
          arguments = listOf(NativeValue.Text("db:x"))
        ),
        rows = listOf(defaulted(id = 1L, token = Token("x")))
      ),
      Case(
        label = "explicit NULL and omitted column",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.TOKEN)
          .VALUES(null, Token("n"))
          .VALUES("Ada", Token("a")),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "token") VALUES (?, ?), (?, ?)""",
          arguments = listOf(
            NativeValue.Null,
            NativeValue.Text("db:n"),
            NativeValue.Text("Ada"),
            NativeValue.Text("db:a")
          )
        ),
        rows = listOf(
          defaulted(
            id = 1L,
            name = null,
            token = Token("n")
          ),
          defaulted(
            id = 2L,
            name = "Ada",
            token = Token("a")
          )
        )
      ),
      Case(
        label = "infix one-column list",
        query = INSERT INTO PERSON COLUMNS PERSON.AGE VALUES listOf(Row1(40), Row1(41)),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("age") VALUES (?), (?)""",
          arguments = listOf(NativeValue.Integer(40), NativeValue.Integer(41))
        ),
        rows = listOf(defaulted(id = 1L, age = 40), defaulted(id = 2L, age = 41))
      ),
      Case(
        label = "infix list after a row and between empty lists",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE, PERSON.TOKEN)
          .VALUES(emptyList())
          .VALUES(1, Token("a")) VALUES listOf(Row2(2, Token("b")), Row2(3, Token("c"))) VALUES emptyList(),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("age", "token") VALUES (?, ?), (?, ?), (?, ?)""",
          arguments = listOf(
            NativeValue.Integer(1),
            NativeValue.Text("db:a"),
            NativeValue.Integer(2),
            NativeValue.Text("db:b"),
            NativeValue.Integer(3),
            NativeValue.Text("db:c")
          )
        ),
        rows = listOf(
          defaulted(
            id = 1L,
            age = 1,
            token = Token("a")
          ),
          defaulted(
            id = 2L,
            age = 2,
            token = Token("b")
          ),
          defaulted(
            id = 3L,
            age = 3,
            token = Token("c")
          )
        )
      ),
      Case(
        label = "infix expression",
        query = INSERT INTO PERSON COLUMNS PERSON.AGE VALUES "abs(?)".expr(INT, -40),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("age") VALUES (abs(?))""",
          arguments = listOf(NativeValue.Integer(-40))
        ),
        rows = listOf(defaulted(id = 1L, age = 40))
      ),
      Case(
        label = "infix conflict algorithm",
        query = INSERT OR CONFLICT_REPLACE INTO PERSON COLUMNS PERSON.NAME VALUES "Ada",
        plan = PlanSnapshot(
          sql = """INSERT OR REPLACE INTO "main"."person" ("name") VALUES (?)""",
          arguments = listOf(NativeValue.Text("Ada"))
        ),
        rows = listOf(defaulted(id = 1L, name = "Ada"))
      ),
      Case(
        label = "fluent conflict algorithm",
        query = INSERT
          .OR(CONFLICT_IGNORE)
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME)
          .VALUES("Ada"),
        plan = PlanSnapshot(
          sql = """INSERT OR IGNORE INTO "main"."person" ("name") VALUES (?)""",
          arguments = listOf(NativeValue.Text("Ada"))
        ),
        rows = listOf(defaulted(id = 1L, name = "Ada"))
      ),
      Case(
        label = "DEFAULT VALUES",
        query = INSERT
          .INTO(PERSON)
          .DEFAULT_VALUES(),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" DEFAULT VALUES""",
          arguments = emptyList()
        ),
        rows = listOf(defaulted(id = 1L))
      )
    )
      .forEach { case ->
        assertWithMessage(case.label)
          .that(snapshot(case.query.compile()))
          .isEqualTo(case.plan)
        assertWithMessage(case.label)
          .that(
            outcome(
              query = case.query,
              database = TestDatabase(
                "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT DEFAULT 'Anon', " +
                    "age INTEGER NOT NULL DEFAULT 7, parent_id INTEGER, token TEXT NOT NULL DEFAULT 'db:d', " +
                    "flag INTEGER DEFAULT 1)"
              )
            )
          )
          .isEqualTo(
            Outcome(
              affected = case.rows.size,
              rows = case.rows,
              triggers = listOf(setOf("person"))
            )
          )
      }
  }

  @Test
  fun `each column count binds its row values in column order`() {
    data class Case(
      val label: String,
      val statements: List<MutationQuery>
    )

    val wide = object : Table<Any>(
      name = "wide",
      alias = null
    ) {
      val C1 = column("c1")
      val C2 = column("c2")
      val C3 = column("c3")
      val C4 = column("c4")
      val C5 = column("c5")
      val C6 = column("c6")
      val C7 = column("c7")
      val C8 = column("c8")
      val C9 = column("c9")

      private fun column(name: String) = Column<Any, Long, Required, Long, Long, Required, Long>(
        table = this,
        name = name,
        nullness = Required,
        parameterEncoding = LONG.parameterEncoding()
      )
    }

    listOf(
      Case(
        label = "1 column",
        statements = listOf(
          INSERT INTO wide COLUMNS wide.C1 VALUES 1L,
          INSERT INTO wide COLUMNS wide.C1 VALUES 1L.asValue(),
          INSERT INTO wide COLUMNS wide.C1 VALUES listOf(Row1(1L))
        )
      ),
      Case(
        label = "2 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2)
            .VALUES(1L, 2L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2)
            .VALUES(1L.asValue(), 2L.asValue()),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2)
            .VALUES(listOf(Row2(1L, 2L)))
        )
      ),
      Case(
        label = "3 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3)
            .VALUES(1L, 2L, 3L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3)
            .VALUES(1L.asValue(), 2L.asValue(), 3L.asValue()),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3)
            .VALUES(listOf(Row3(1L, 2L, 3L)))
        )
      ),
      Case(
        label = "4 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4)
            .VALUES(1L, 2L, 3L, 4L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4)
            .VALUES(1L.asValue(), 2L.asValue(), 3L.asValue(), 4L.asValue()),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4)
            .VALUES(listOf(Row4(1L, 2L, 3L, 4L)))
        )
      ),
      Case(
        label = "5 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5)
            .VALUES(1L, 2L, 3L, 4L, 5L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5)
            .VALUES(1L.asValue(), 2L.asValue(), 3L.asValue(), 4L.asValue(), 5L.asValue()),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5)
            .VALUES(listOf(Row5(1L, 2L, 3L, 4L, 5L)))
        )
      ),
      Case(
        label = "6 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6)
            .VALUES(1L, 2L, 3L, 4L, 5L, 6L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6)
            .VALUES(1L.asValue(), 2L.asValue(), 3L.asValue(), 4L.asValue(), 5L.asValue(), 6L.asValue()),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6)
            .VALUES(listOf(Row6(1L, 2L, 3L, 4L, 5L, 6L)))
        )
      ),
      Case(
        label = "7 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7)
            .VALUES(1L, 2L, 3L, 4L, 5L, 6L, 7L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7)
            .VALUES(1L.asValue(), 2L.asValue(), 3L.asValue(), 4L.asValue(), 5L.asValue(), 6L.asValue(), 7L.asValue()),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7)
            .VALUES(listOf(Row7(1L, 2L, 3L, 4L, 5L, 6L, 7L)))
        )
      ),
      Case(
        label = "8 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8)
            .VALUES(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8)
            .VALUES(
              1L.asValue(),
              2L.asValue(),
              3L.asValue(),
              4L.asValue(),
              5L.asValue(),
              6L.asValue(),
              7L.asValue(),
              8L.asValue()
            ),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8)
            .VALUES(listOf(Row8(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)))
        )
      ),
      Case(
        label = "9 columns",
        statements = listOf(
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8, wide.C9)
            .VALUES(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8, wide.C9)
            .VALUES(
              1L.asValue(),
              2L.asValue(),
              3L.asValue(),
              4L.asValue(),
              5L.asValue(),
              6L.asValue(),
              7L.asValue(),
              8L.asValue(),
              9L.asValue()
            ),
          INSERT
            .INTO(wide)
            .COLUMNS(wide.C1, wide.C2, wide.C3, wide.C4, wide.C5, wide.C6, wide.C7, wide.C8, wide.C9)
            .VALUES(listOf(Row9(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)))
        )
      )
    )
      .forEachIndexed { index, case ->
        val count = index + 1
        val columns = (1..count).joinToString { "\"c$it\"" }
        val placeholders = List(count) { "?" }.joinToString()
        val values = (1L..count).toList()
        TestDatabase(
          "CREATE TABLE wide (c1 INTEGER DEFAULT 0, c2 INTEGER DEFAULT 0, c3 INTEGER DEFAULT 0, " +
              "c4 INTEGER DEFAULT 0, c5 INTEGER DEFAULT 0, c6 INTEGER DEFAULT 0, c7 INTEGER DEFAULT 0, " +
              "c8 INTEGER DEFAULT 0, c9 INTEGER DEFAULT 0)"
        ).use { database ->
          case.statements.forEach { statement ->
            assertWithMessage(case.label)
              .that(snapshot(statement.compile()))
              .isEqualTo(
                PlanSnapshot(
                  sql = """INSERT INTO "main"."wide" ($columns) VALUES ($placeholders)""",
                  arguments = values.map(NativeValue::Integer)
                )
              )
            statement
              .usingConnection(database.connection)
              .execute()
          }
          assertWithMessage(case.label)
            .that(database.query("SELECT c1, c2, c3, c4, c5, c6, c7, c8, c9 FROM wide") { List(9, it::getLong) })
            .isEqualTo(List(3) { values + List(9 - count) { 0L } })
        }
      }
  }

  @Test
  fun `host values bind natively through the column encoders`() {
    val insert = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE, PERSON.TOKEN, PERSON.FLAG)
      .VALUES(30, Token("x"), false)
      .VALUES(listOf(Row3(31, Token("y"), null)))

    personDatabase().use { database ->
      insert
        .usingConnection(database.connection)
        .execute()

      val stored = database.query(
        "SELECT typeof(age), token, typeof(flag), quote(flag) FROM person WHERE id > 4"
      ) { row ->
        listOf(row.getText(0), row.getText(1), row.getText(2), row.getText(3))
      }
      assertThat(stored)
        .isEqualTo(
          listOf(
            listOf("integer", "db:x", "integer", "0"),
            listOf("integer", "db:y", "null", "NULL")
          )
        )
    }
  }

  @Test
  fun `expression rows take parameters, functions and scalar subqueries`() {
    val name = PERSON.NAME.asParameter("name")
    val age = PERSON.AGE.asParameter("age")
    val gracesAge = SELECT
      .COLUMN(PERSON.AGE)
      .FROM(PERSON)
      .WHERE(PERSON.ID IS 2L)
      .asScalar()
    val insert = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.NAME, PERSON.AGE, PERSON.PARENT_ID, PERSON.TOKEN)
      .VALUES(name, age, 1L.asValue(), Token("p").asValue(TOKEN_TYPE))
      .VALUES("lower(?)".expr(STRING, "ADA"), gracesAge, "abs(?)".expr(LONG, -2L), Token("q").asValue(TOKEN_TYPE))

    val bound = insert.bind(name TO "Augusta", age TO 36)

    assertThat(bound.compile().sql)
      .isEqualTo(
        """INSERT INTO "main"."person" ("name", "age", "parent_id", "token") VALUES (?, ?, ?, ?), """ +
            """(lower(?), (SELECT "person"."age" FROM "main"."person" WHERE "person"."id" = ?), abs(?), ?)"""
      )
    assertThat(outcome(bound))
      .isEqualTo(
        Outcome(
          affected = 2,
          rows = people + listOf(
            Person(
              id = 5L,
              name = "Augusta",
              age = 36,
              parentId = 1L,
              token = Token("p"),
              flag = null
            ),
            Person(
              id = 6L,
              name = "ada",
              age = 85,
              parentId = 2L,
              token = Token("q"),
              flag = null
            )
          ),
          triggers = listOf(setOf("person"))
        )
      )
    assertThat(assertThrows(IllegalArgumentException::class.java) { insert.compile() }.message)
      .isEqualTo("Missing parameter 'name'")
  }

  @Test
  fun `nullable scalar subqueries leave NOT NULL to SQLite`() {
    fun insert(id: Long) = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE, PERSON.TOKEN)
      .VALUES(
        SELECT
          .COLUMN(PERSON.AGE)
          .FROM(PERSON)
          .WHERE(PERSON.ID IS id)
          .asScalar(),
        Token("s").asValue(TOKEN_TYPE)
      )

    assertThat(listOf(outcome(insert(1L)), outcome(insert(99L))))
      .isEqualTo(
        listOf(
          Outcome(
            affected = 1,
            rows = people + Person(
              id = 5L,
              name = null,
              age = 36,
              parentId = null,
              token = Token("s"),
              flag = null
            ),
            triggers = listOf(setOf("person"))
          ),
          Outcome(
            affected = null,
            rows = people,
            triggers = listOf(setOf("person"))
          )
        )
      )
    personDatabase().use { database ->
      assertThat(
        sqliteErrorMessage {
          insert(99L)
            .usingConnection(database.connection)
            .execute()
        }
      )
        .isEqualTo("NOT NULL constraint failed: person.age")
    }
  }

  @Test
  fun `readAs narrows the read type of numbers and keeps the SQL`() {
    val count = SELECT
      .COLUMN(COUNT())
      .FROM(PERSON)
      .asScalar()
      .readAs(INT)
    val insert = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE, PERSON.PARENT_ID, PERSON.TOKEN)
      .VALUES(count, (40.asValue() + 1).readAs(LONG), Token("r").asValue(TOKEN_TYPE))
    val older = SELECT
      .COLUMN((PERSON.AGE + 1).readAs(INT))
      .FROM(PERSON)
      .WHERE((PERSON.AGE + 1).readAs(INT) GREATER_THAN 20)
    val total = SELECT
      .COLUMN(SUM(PERSON.AGE).readAs(INT))
      .FROM(PERSON)
      .WHERE(PERSON.ID IS 99L)

    assertThat(listOf(insert.compile().sql, older.compile().sql, total.compile().sql))
      .isEqualTo(
        listOf(
          """INSERT INTO "main"."person" ("age", "parent_id", "token") """ +
              """VALUES ((SELECT COUNT(*) FROM "main"."person"), ? + ?, ?)""",
          """SELECT "person"."age" + ? FROM "main"."person" WHERE ("person"."age" + ?) > ?""",
          """SELECT SUM("person"."age") FROM "main"."person" WHERE "person"."id" = ?"""
        )
      )
    assertThat(outcome(insert).rows.last())
      .isEqualTo(
        Person(
          id = 5L,
          name = null,
          age = 4,
          parentId = 41L,
          token = Token("r"),
          flag = null
        )
      )
    personDatabase().use { database ->
      val olderAges: List<Int> = older.rows(database)
      val totals: List<Int?> = total.rows(database)
      assertThat(listOf(olderAges, totals)).isEqualTo(listOf(listOf(37, 86), listOf(null)))
    }
  }

  @Test
  fun `bare fields, aliased targets and aliased columns reject before SQL`() {
    val alias = PERSON.AS("p")
    val connection = mock<DbConnectionImpl>()

    assertThat(
      listOf(
        { INSERT INTO alias },
        { INSERT INTO PERSON COLUMNS alias.NAME },
        {
          (INSERT INTO PERSON COLUMNS PERSON.AGE VALUES PERSON.AGE)
            .usingConnection(connection)
            .execute()
        },
        {
          (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES PET.NAME)
            .usingConnection(connection)
            .execute()
        }
      )
        .map { construction ->
          assertThrows(IllegalArgumentException::class.java) { construction() }.message
        }
    )
      .isEqualTo(
        listOf(
          "INSERT targets cannot be aliased; use person itself",
          "INSERT column name must be a field of the inserted person occurrence",
          "INSERT VALUES cannot read a field outside a subquery",
          "INSERT VALUES cannot read a field outside a subquery"
        )
      )
    verifyNoInteractions(connection)
  }

  @Test
  fun `list rows prepare outside the statement cache and other rows use it`() {
    val recording = newConnection()
    val listed = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES listOf(Row1("Ada"), Row1(null)))
      .usingConnection(recording.dbConnection)
      .compile()
    val fixed = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada")
      .usingConnection(recording.dbConnection)
      .compile()

    assertThat(List(2) { listed.execute() } + List(2) { fixed.execute() }).isEqualTo(List(4) { 1 })
    assertThat(recording.sqliteConnection.compiledStatements.map { it.sql to it.closeCalls })
      .isEqualTo(
        listOf(
          """INSERT INTO "main"."person" ("name") VALUES (?), (?)""" to 1,
          """INSERT INTO "main"."person" ("name") VALUES (?), (?)""" to 1,
          """INSERT INTO "main"."person" ("name") VALUES (?)""" to 0
        )
      )
    assertThat(recording.sqliteConnection.executions.map(RecordedExecution::bindings))
      .isEqualTo(
        listOf(
          mapOf(1 to "Ada", 2 to null),
          mapOf(1 to "Ada", 2 to null),
          mapOf(1 to "Ada"),
          mapOf(1 to "Ada")
        )
      )
  }

  @Test
  fun `list rows bind more than 999 values in one statement`() {
    val rows = List(400) { Row3(it, Token("t$it"), it % 2 == 0) }
    val insert = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE, PERSON.TOKEN, PERSON.FLAG)
      .VALUES(rows)

    personDatabase().use { database ->
      assertThat(
        insert
          .usingConnection(database.connection)
          .execute()
      )
        .isEqualTo(400)
      assertThat(
        SELECT
          .COLUMNS(PERSON.AGE, PERSON.TOKEN, PERSON.FLAG)
          .FROM(PERSON)
          .WHERE(PERSON.ID GREATER_THAN 4L)
          .rows(database)
      )
        .isEqualTo(rows)
    }
  }

  @Test
  fun `statements whose rows all come from empty lists run no SQL`() {
    val unknown = PERSON.AGE.asParameter("unknown")
    val recording = newConnection()
    val empty = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.NAME, PERSON.TOKEN)
      .VALUES(emptyList())
      .VALUES(emptyList())
      .usingConnection(recording.dbConnection)

    assertThat(snapshot(empty.compile()))
      .isEqualTo(
        PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "token") SELECT NULL, NULL WHERE 0""",
          arguments = emptyList()
        )
      )
    assertThat(listOf(empty.execute(), empty.compile().execute())).isEqualTo(listOf(0, 0))
    assertThat(recording.sqliteConnection.compiledStatements).isEmpty()
    assertThat(recording.triggers).isEmpty()
    assertThat(assertThrows(IllegalArgumentException::class.java) { empty.bind(unknown TO 1).execute() }.message)
      .isEqualTo("Unexpected parameter 'unknown'")
  }

  @Test
  fun `counts follow changes and notifications follow UPDATE`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val outcome: Outcome
    )

    val conflicting = INSERT
      .OR(CONFLICT_IGNORE)
      .INTO(PERSON)
      .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
      .VALUES(1L, 1, Token("x"))
    val inserted = Person(
      id = 9L,
      name = null,
      age = 2,
      parentId = null,
      token = Token("y"),
      flag = null
    )
    listOf(
      Case(
        label = "partly ignored",
        query = conflicting.VALUES(9L, 2, Token("y")),
        outcome = Outcome(
          affected = 1,
          rows = people + inserted,
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "all ignored",
        query = conflicting,
        outcome = Outcome(
          affected = 0,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "failure after execution starts",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
          .VALUES(9L, 2, Token("y"))
          .VALUES(1L, 1, Token("x")),
        outcome = Outcome(
          affected = null,
          rows = people,
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "failure when the statement compiles",
        query = INSERT INTO PET COLUMNS PET.NAME VALUES "Rex",
        outcome = Outcome(
          affected = null,
          rows = people,
          triggers = emptyList()
        )
      )
    )
      .forEach { case ->
        assertWithMessage(case.label)
          .that(outcome(case.query))
          .isEqualTo(case.outcome)
      }
  }

  @Test
  fun `REPLACE notifies the tables its deletes cascade into`() {
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf(
          "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT, age INTEGER NOT NULL, parent_id INTEGER, " +
              "token TEXT NOT NULL, flag INTEGER)"
        ),
        cascadeTables = mapOf("person" to arrayOf("person", "pet"))
      ),
      driver = BundledSQLiteDriver()
    )
    val recorder = connection.recordTriggers()
    val replaced = INSERT
      .OR(CONFLICT_REPLACE)
      .INTO(PERSON)
      .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
      .VALUES(1L, 2, Token("b"))
    val plain = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE, PERSON.TOKEN)
      .VALUES(3, Token("c"))

    try {
      val affected = listOf(plain, replaced, replaced).map {
        it
          .usingConnection(connection)
          .execute()
      }

      assertThat(affected).isEqualTo(listOf(1, 1, 1))
      assertThat(recorder.triggers)
        .isEqualTo(listOf(setOf("person"), setOf("person", "pet"), setOf("person", "pet")))
      assertThat(connection.persons())
        .isEqualTo(
          listOf(
            Person(
              id = 1L,
              name = null,
              age = 2,
              parentId = null,
              token = Token("b"),
              flag = null
            )
          )
        )
    } finally {
      connection.pool.close()
    }
  }

  @Test
  fun `notifications queue until the enclosing transaction commits`() {
    personDatabase().use { database ->
      val connection = database.connection
      val recorder = connection.recordTriggers()
      val transaction = connection.newTransaction()
      val queued = try {
        INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE, PERSON.TOKEN)
          .VALUES(listOf(Row2(1, Token("t"))))
          .usingConnection(connection)
          .execute()
        recorder.triggers.toList().also { transaction.markSuccessful() }
      } finally {
        transaction.end()
      }

      assertThat(listOf(queued, recorder.triggers)).isEqualTo(listOf(emptyList(), listOf(setOf("person"))))
    }
  }

  @Test
  fun `temporary tables and views are inserted into as SQLite defines`() {
    val session = object : Table<Any>(
      name = "session",
      alias = null,
      temporary = true
    ) {
      val NAME = Column<Any, String, Required, String, String, Required, String>(
        table = this,
        name = "name",
        nullness = Required,
        parameterEncoding = STRING.parameterEncoding()
      )
    }
    val sessionInsert = INSERT INTO session COLUMNS session.NAME VALUES "Ada"
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        temporarySchema = listOf("CREATE TEMP TABLE session (name TEXT NOT NULL)")
      )
    )
    val recorder = connection.recordTriggers()

    try {
      assertThat(sessionInsert.compile().sql).isEqualTo("""INSERT INTO "temp"."session" ("name") VALUES (?)""")
      assertThat(
        sessionInsert
          .usingConnection(connection)
          .execute()
      )
        .isEqualTo(1)
      assertThat(
        SELECT
          .COLUMN(session.NAME)
          .FROM(session)
          .usingConnection(connection)
          .execute()
      )
        .isEqualTo(listOf("Ada"))
      assertThat(recorder.triggers).isEqualTo(listOf(setOf("session")))
    } finally {
      connection.pool.close()
    }

    val viewInsert = INSERT INTO AUTHOR_SUMMARY COLUMNS AUTHOR_SUMMARY.NAME VALUES "Edith"
    libraryDatabase().use { database ->
      database.createViews(AUTHOR_SUMMARY_VIEW)
      database.execSQL(
        "CREATE TRIGGER author_summary_insert INSTEAD OF INSERT ON author_summary " +
            "BEGIN INSERT INTO author (name) VALUES (NEW.name); END"
      )
      val viewRecorder = database.connection.recordTriggers()

      assertThat(viewInsert.compile().sql).isEqualTo("""INSERT INTO "main"."author_summary" ("name") VALUES (?)""")
      assertThat(
        viewInsert
          .usingConnection(database.connection)
          .execute()
      )
        .isEqualTo(0)
      assertThat(database.query("SELECT name FROM author WHERE id = 4") { it.getText(0) }).isEqualTo(listOf("Edith"))
      assertThat(viewRecorder.triggers).isEmpty()
    }
  }

  @Test
  fun `raw INSERT notifies its declared tables`() {
    val fluent = INSERT
      .RAW("INSERT INTO person (age, token) VALUES (?, ?)", 50, "db:r")
      .notifies(PERSON)
    val infix = (INSERT RAW "INSERT INTO person (age, token) VALUES (51, 'db:s')").notifies(PERSON)

    val outcomes = listOf(fluent, infix).map { statement ->
      personDatabase().use { database ->
        val connection = database.connection
        val recorder = connection.recordTriggers()
        Outcome(
          affected = statement
            .usingConnection(connection)
            .execute(),
          rows = connection.persons(),
          triggers = recorder.triggers
        )
      }
    }

    assertThat(outcomes)
      .isEqualTo(
        listOf(50 to "r", 51 to "s").map { (age, token) ->
          Outcome(
            affected = 1,
            rows = people + Person(
              id = 5L,
              name = null,
              age = age,
              parentId = null,
              token = Token(token),
              flag = null
            ),
            triggers = listOf(setOf("person"))
          )
        }
      )
  }
}
