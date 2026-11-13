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

  private data class RowIdOutcome(
    val rowId: Long?,
    val rows: List<Person>,
    val triggers: List<Set<String>>,
    val failure: String? = null
  )

  private fun rowIdOutcome(
    query: RowIdInsertQuery,
    database: TestDatabase
  ) = database.use {
    val connection = database.connection
    val recorder = connection.recordTriggers()
    val rowId = runCatching {
      query
        .usingConnection(connection)
        .execute()
    }
    RowIdOutcome(
      rowId = rowId.getOrNull(),
      rows = connection.persons(),
      triggers = recorder.triggers,
      failure = rowId
        .exceptionOrNull()
        ?.message
        ?.substringAfter("message: ")
    )
  }

  private fun defaultedPersonDatabase() = TestDatabase(
    "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT DEFAULT 'Anon', " +
        "age INTEGER NOT NULL DEFAULT 7, parent_id INTEGER, token TEXT NOT NULL DEFAULT 'db:d', " +
        "flag INTEGER DEFAULT 1)"
  )

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
              database = defaultedPersonDatabase()
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
      val replacedRowId = replaced
        .readRowId()
        .usingConnection(connection)
        .execute()

      assertThat(affected).isEqualTo(listOf(1, 1, 1))
      assertThat(replacedRowId).isEqualTo(1L)
      assertThat(recorder.triggers)
        .isEqualTo(
          listOf(
            setOf("person"),
            setOf("person", "pet"),
            setOf("person", "pet"),
            setOf("person", "pet")
          )
        )
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
      val rowId: Long?
      val queued = try {
        INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE, PERSON.TOKEN)
          .VALUES(listOf(Row2(1, Token("t"))))
          .usingConnection(connection)
          .execute()
        rowId = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE, PERSON.TOKEN)
          .VALUES(2, Token("u"))
          .readRowId()
          .usingConnection(connection)
          .execute()
        recorder.triggers.toList().also { transaction.markSuccessful() }
      } finally {
        transaction.end()
      }

      assertThat(rowId).isEqualTo(6L)
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

  @Test
  fun `readRowId reads the row ID of the inserted row and notifies as the count does`() {
    data class Case(
      val label: String,
      val query: RowIdInsertQuery,
      val database: () -> TestDatabase = ::personDatabase,
      val outcome: RowIdOutcome
    )

    val row = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
    val inserted = Person(
      id = 5L,
      name = null,
      age = 2,
      parentId = null,
      token = Token("y"),
      flag = null
    )
    listOf(
      Case(
        label = "host row",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE, PERSON.TOKEN)
          .VALUES(2, Token("y"))
          .readRowId(),
        outcome = RowIdOutcome(
          rowId = 5L,
          rows = people + inserted,
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "infix expression row",
        query = (INSERT INTO PERSON COLUMNS PERSON.AGE VALUES "abs(?)".expr(INT, -40)).readRowId(),
        database = ::defaultedPersonDatabase,
        outcome = RowIdOutcome(
          rowId = 1L,
          rows = listOf(defaulted(id = 1L, age = 40)),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "DEFAULT VALUES",
        query = INSERT
          .INTO(PERSON)
          .DEFAULT_VALUES()
          .readRowId(),
        database = ::defaultedPersonDatabase,
        outcome = RowIdOutcome(
          rowId = 1L,
          rows = listOf(defaulted(id = 1L)),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "explicit row ID -1",
        query = row
          .VALUES(-1L, 2, Token("y"))
          .readRowId(),
        outcome = RowIdOutcome(
          rowId = -1L,
          rows = listOf(inserted.copy(id = -1L)) + people,
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "ignored",
        query = INSERT
          .OR(CONFLICT_IGNORE)
          .INTO(PERSON)
          .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
          .VALUES(1L, 2, Token("y"))
          .readRowId(),
        outcome = RowIdOutcome(
          rowId = null,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "replaced",
        query = INSERT
          .OR(CONFLICT_REPLACE)
          .INTO(PERSON)
          .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
          .VALUES(1L, 2, Token("y"))
          .readRowId(),
        outcome = RowIdOutcome(
          rowId = 1L,
          rows = listOf(inserted.copy(id = 1L)) + people.drop(1),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "failure after execution starts",
        query = row
          .VALUES(1L, 2, Token("y"))
          .readRowId(),
        outcome = RowIdOutcome(
          rowId = null,
          rows = people,
          triggers = listOf(setOf("person")),
          failure = "UNIQUE constraint failed: person.id"
        )
      )
    )
      .forEach { case ->
        assertWithMessage(case.label)
          .that(
            rowIdOutcome(
              query = case.query,
              database = case.database()
            )
          )
          .isEqualTo(case.outcome)
      }
  }

  @Test
  fun `readRowId binds parameters and its compiled statement runs again`() {
    val age = PERSON.AGE.asParameter("age")
    val token = PERSON.TOKEN.asParameter("token")
    val insert = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE, PERSON.TOKEN)
      .VALUES(age, token)
      .readRowId()

    personDatabase().use { database ->
      val connection = database.connection
      val recorder = connection.recordTriggers()
      val compiled = insert
        .bind(age TO 3, token TO Token("p"))
        .usingConnection(connection)
        .compile()

      assertThat(compiled.sql).isEqualTo("""INSERT INTO "main"."person" ("age", "token") VALUES (?, ?)""")
      assertThat(listOf(compiled.execute(), compiled.execute())).isEqualTo(listOf(5L, 6L))
      assertThat(connection.persons())
        .isEqualTo(
          people + listOf(5L, 6L).map { id ->
            Person(
              id = id,
              name = null,
              age = 3,
              parentId = null,
              token = Token("p"),
              flag = null
            )
          }
        )
      assertThat(recorder.triggers).isEqualTo(List(2) { setOf("person") })
    }
    assertThat(assertThrows(IllegalArgumentException::class.java) { insert.compile() }.message)
      .isEqualTo("Missing parameter 'age'")
  }

  @Test
  fun `readRowId rejects WITHOUT ROWID tables when the statement is built`() {
    val setting = object : Table<Any>(
      name = "setting",
      alias = null,
      withoutRowId = true
    ) {
      val KEY = Column<Any, String, Required, String, String, Required, String>(
        table = this,
        name = "key",
        nullness = Required,
        parameterEncoding = STRING.parameterEncoding()
      )
    }
    val row = INSERT INTO setting COLUMNS setting.KEY VALUES "theme"

    assertThat(row.compile().sql).isEqualTo("""INSERT INTO "main"."setting" ("key") VALUES (?)""")
    assertThat(
      listOf(
        { row.readRowId() },
        {
          INSERT
            .INTO(setting)
            .DEFAULT_VALUES()
            .readRowId()
        },
        {
          INSERT
            .INTO(setting)
            .COLUMNS(*arrayOf(setting.KEY))
            .VALUES { it[setting.KEY] = "theme" }
            .readRowId()
        }
      )
        .map { construction ->
          assertThrows(IllegalArgumentException::class.java) { construction() }.message
        }
    )
      .isEqualTo(
        List(3) { "readRowId() cannot read the row ID of an INSERT into setting, a WITHOUT ROWID table" }
      )
  }

  @Test
  fun `INSERT SELECT renders the query after the column list and inserts its rows`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val plan: PlanSnapshot,
      val inserted: List<Person>
    )

    val minAge = PERSON.AGE.asParameter("min")
    val adults = SELECT
      .COLUMNS(PERSON.NAME, PERSON.AGE)
      .FROM(PERSON)
      .WHERE(PERSON.AGE GREATER_THAN 30) AS "adults"
    listOf(
      Case(
        label = "infix one column",
        query = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (
          SELECT COLUMN PERSON.AGE FROM PERSON WHERE (PERSON.NAME IS "Ada")
        ),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("age") SELECT "person"."age" FROM "main"."person" """ +
              """WHERE "person"."name" = ?""",
          arguments = listOf(NativeValue.Text("Ada"))
        ),
        inserted = listOf(defaulted(id = 4L, age = 36))
      ),
      Case(
        label = "several columns filtered by WHERE",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE)
          .SELECT(
            SELECT
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.AGE GREATER_THAN 30)
          ),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age") SELECT "person"."name", "person"."age" """ +
              """FROM "main"."person" WHERE "person"."age" > ?""",
          arguments = listOf(NativeValue.Integer(30))
        ),
        inserted = listOf(
          defaulted(
            id = 4L,
            name = "Ada",
            age = 36
          ),
          defaulted(
            id = 5L,
            name = "Grace",
            age = 85
          )
        )
      ),
      Case(
        label = "infix compound",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE) SELECT (
            SELECT
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 3L) UNION_ALL SELECT
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 1L)
          ),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age") SELECT "person"."name", "person"."age" """ +
              """FROM "main"."person" WHERE "person"."id" = ? UNION ALL SELECT "person"."name", "person"."age" """ +
              """FROM "main"."person" WHERE "person"."id" = ?""",
          arguments = listOf(NativeValue.Integer(3), NativeValue.Integer(1))
        ),
        inserted = listOf(
          defaulted(
            id = 4L,
            name = null,
            age = 17
          ),
          defaulted(
            id = 5L,
            name = "Ada",
            age = 36
          )
        )
      ),
      Case(
        label = "derived source",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE)
          .SELECT(
            SELECT
              .COLUMNS(adults[PERSON.NAME], adults[PERSON.AGE])
              .FROM(adults)
          ),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age") SELECT "adults"."person.name", """ +
              """"adults"."person.age" FROM (SELECT "person"."name" AS "person.name", "person"."age" AS """ +
              """"person.age" FROM "main"."person" WHERE "person"."age" > ?) AS "adults"""",
          arguments = listOf(NativeValue.Integer(30))
        ),
        inserted = listOf(
          defaulted(
            id = 4L,
            name = "Ada",
            age = 36
          ),
          defaulted(
            id = 5L,
            name = "Grace",
            age = 85
          )
        )
      ),
      Case(
        label = "leading WITH",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE)
          .SELECT(
            WITH(
              SELECT
                .COLUMNS(PERSON.NAME, PERSON.AGE)
                .FROM(PERSON)
                .WHERE(PERSON.AGE LESS_THAN 20)
            ) { minors ->
              SELECT
                .COLUMNS(minors[PERSON.NAME], minors[PERSON.AGE])
                .FROM(minors)
            }
          ),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age") WITH "cte1" AS (SELECT "person"."name" AS """ +
              """"person.name", "person"."age" AS "person.age" FROM "main"."person" WHERE "person"."age" < ?) """ +
              """SELECT "cte1"."person.name", "cte1"."person.age" FROM "cte1"""",
          arguments = listOf(NativeValue.Integer(20))
        ),
        inserted = listOf(
          defaulted(
            id = 4L,
            name = null,
            age = 17
          )
        )
      ),
      Case(
        label = "mapTo on a row query",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE)
          .SELECT(
            SELECT
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 2L)
              .mapTo { name, age -> "$name $age" }
          ),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age") SELECT "person"."name", "person"."age" """ +
              """FROM "main"."person" WHERE "person"."id" = ?""",
          arguments = listOf(NativeValue.Integer(2))
        ),
        inserted = listOf(
          defaulted(
            id = 4L,
            name = "Grace",
            age = 85
          )
        )
      ),
      Case(
        label = "readAs and mapTo on a scalar query",
        query = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT SELECT
          .COLUMN(PERSON.AGE)
          .FROM(PERSON)
          .WHERE(PERSON.ID IS 3L)
          .readAs(STRING)
          .mapTo(::Token),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("age") SELECT "person"."age" FROM "main"."person" """ +
              """WHERE "person"."id" = ?""",
          arguments = listOf(NativeValue.Integer(3))
        ),
        inserted = listOf(defaulted(id = 4L, age = 17))
      ),
      Case(
        label = "parameter bound through bind",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE)
          .SELECT(
            SELECT
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.AGE GREATER_THAN minAge)
          )
          .bind(minAge TO 80),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age") SELECT "person"."name", "person"."age" """ +
              """FROM "main"."person" WHERE "person"."age" > ?""",
          arguments = listOf(NativeValue.Integer(80))
        ),
        inserted = listOf(
          defaulted(
            id = 4L,
            name = "Grace",
            age = 85
          )
        )
      ),
      Case(
        label = "number read as INT",
        query = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN COUNT().readAs(INT) FROM PERSON),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("age") SELECT COUNT(*) FROM "main"."person"""",
          arguments = emptyList()
        ),
        inserted = listOf(defaulted(id = 4L, age = 3))
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
              database = defaultedPersonDatabase().apply {
                execSQL("INSERT INTO person (name, age) VALUES ('Ada', 36), ('Grace', 85), (NULL, 17)")
              }
            )
          )
          .isEqualTo(
            Outcome(
              affected = case.inserted.size,
              rows = listOf(
                defaulted(
                  id = 1L,
                  name = "Ada",
                  age = 36
                ),
                defaulted(
                  id = 2L,
                  name = "Grace",
                  age = 85
                ),
                defaulted(
                  id = 3L,
                  name = null,
                  age = 17
                )
              ) + case.inserted,
              triggers = listOf(setOf("person"))
            )
          )
      }
  }

  @Test
  fun `INSERT SELECT leaves NOT NULL to SQLite`() {
    fun insert(id: Long) = INSERT
      .INTO(PET)
      .COLUMNS(PET.OWNER, PET.NAME)
      .SELECT(
        SELECT
          .COLUMNS(PERSON.ID, PERSON.NAME)
          .FROM(PERSON)
          .WHERE(PERSON.ID IS id)
      )

    petDatabase().use { database ->
      val connection = database.connection
      val failure = sqliteErrorMessage {
        insert(3L)
          .usingConnection(connection)
          .execute()
      }

      assertThat(
        insert(1L)
          .usingConnection(connection)
          .execute()
      )
        .isEqualTo(1)
      assertThat(failure).isEqualTo("NOT NULL constraint failed: pet.name")
      assertThat(
        SELECT
          .FROM(PET)
          .WHERE(PET.ID GREATER_THAN 3L)
          .rows(database)
      )
        .isEqualTo(
          listOf(
            Pet(
              id = 4L,
              owner = 1L,
              name = "Ada",
              nickname = null
            )
          )
        )
    }
  }

  @Test
  fun `INSERT SELECT counts follow changes and notifications follow VALUES`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val outcome: Outcome
    )

    val copies = SELECT
      .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
      .FROM(PERSON)
    listOf(
      Case(
        label = "rows inserted",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE, PERSON.TOKEN)
          .SELECT(
            SELECT
              .COLUMNS(PERSON.AGE, PERSON.TOKEN)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 1L)
          ),
        outcome = Outcome(
          affected = 1,
          rows = people + Person(
            id = 5L,
            name = null,
            age = 36,
            parentId = null,
            token = Token("a"),
            flag = null
          ),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "no rows selected",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
          .SELECT(copies.WHERE(PERSON.ID IS 99L)),
        outcome = Outcome(
          affected = 0,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "all ignored",
        query = INSERT
          .OR(CONFLICT_IGNORE)
          .INTO(PERSON)
          .COLUMNS(PERSON.ID, PERSON.AGE, PERSON.TOKEN)
          .SELECT(copies),
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
          .SELECT(copies),
        outcome = Outcome(
          affected = null,
          rows = people,
          triggers = listOf(setOf("person"))
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
  fun `INSERT SELECT with REPLACE notifies the cascade when the enclosing transaction commits`() {
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
      .SELECT(
        SELECT
          .COLUMNS(PERSON.ID, (PERSON.AGE + 1).readAs(INT), PERSON.TOKEN)
          .FROM(PERSON)
      )

    try {
      val inserted = INSERT
        .INTO(PERSON)
        .COLUMNS(PERSON.AGE, PERSON.TOKEN)
        .VALUES(3, Token("c"))
        .usingConnection(connection)
        .execute()
      val transaction = connection.newTransaction()
      val (affected, queued) = try {
        (
          replaced
            .usingConnection(connection)
            .execute() to recorder.triggers.toList()
        ).also { transaction.markSuccessful() }
      } finally {
        transaction.end()
      }

      assertThat(listOf(inserted, affected)).isEqualTo(listOf(1, 1))
      assertThat(listOf(queued, recorder.triggers))
        .isEqualTo(
          listOf(
            listOf(setOf("person")),
            listOf(setOf("person"), setOf("person", "pet"))
          )
        )
      assertThat(connection.persons())
        .isEqualTo(
          listOf(
            Person(
              id = 1L,
              name = null,
              age = 4,
              parentId = null,
              token = Token("c"),
              flag = null
            )
          )
        )
    } finally {
      connection.pool.close()
    }
  }

  @Test
  fun `INSERT SELECT rejects a query with a connection and prepares through the statement cache`() {
    val connection = mock<DbConnectionImpl>()
    val ages = SELECT COLUMN PERSON.AGE FROM PERSON
    val recording = newConnection()
    val copy = (INSERT INTO PERSON COLUMNS PERSON.AGE SELECT ages)
      .usingConnection(recording.dbConnection)
      .compile()

    assertThat(
      listOf(
        { INSERT INTO PERSON COLUMNS PERSON.AGE SELECT ages.usingConnection(connection) },
        {
          INSERT
            .INTO(PERSON)
            .COLUMNS(*arrayOf(PERSON.AGE))
            .SELECT(ages.usingConnection(connection))
        }
      )
        .map { construction ->
          assertThrows(IllegalArgumentException::class.java) { construction() }.message
        }
    )
      .isEqualTo(List(2) { "A nested query cannot use a connection; choose it on the outermost query" })
    verifyNoInteractions(connection)
    assertThat(List(2) { copy.execute() }).isEqualTo(List(2) { 1 })
    assertThat(recording.sqliteConnection.compiledStatements.map { it.sql to it.closeCalls })
      .isEqualTo(
        listOf("""INSERT INTO "main"."person" ("age") SELECT "person"."age" FROM "main"."person"""" to 0)
      )
  }
}
