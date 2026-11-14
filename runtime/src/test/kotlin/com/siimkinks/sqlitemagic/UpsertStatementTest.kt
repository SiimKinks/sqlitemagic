package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

internal class UpsertStatementTest {
  private data class Outcome(
    val affected: Int?,
    val rows: List<Person>,
    val triggers: List<Set<String>>,
    val failure: String? = null
  )

  private fun outcome(query: MutationQuery) = upsertDatabase().use { database ->
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
      triggers = recorder.triggers,
      failure = affected
        .exceptionOrNull()
        ?.message
        ?.substringAfter("message: ")
    )
  }

  private fun upsertDatabase() = TestDatabase(
    "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT UNIQUE, age INTEGER NOT NULL DEFAULT 7, " +
        "parent_id INTEGER, token TEXT NOT NULL DEFAULT 'db:d', flag INTEGER, UNIQUE (parent_id, token))",
    "INSERT INTO person VALUES (1, 'Ada', 36, NULL, 'db:a', 1), (2, 'Grace', 85, 1, 'db:g', 0), " +
        "(3, NULL, 17, 2, 'db:n', NULL), (4, 'Linus', 17, 4, 'db:l', 1)",
    "CREATE TABLE pet (id INTEGER PRIMARY KEY, owner INTEGER NOT NULL, name TEXT NOT NULL, nickname TEXT)",
    "INSERT INTO pet VALUES (1, 1, 'Rex', NULL), (2, 1, 'Tom', 'Tommy'), (3, 2, 'Kit', NULL)"
  )

  @Test
  fun `upsert clauses render after the rows and resolve conflicts as SQLite defines`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val plan: PlanSnapshot,
      val outcome: Outcome
    )

    fun newerAge(age: Int) = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.NAME, PERSON.AGE, PERSON.TOKEN)
      .VALUES("Ada", age, Token("x"))
      .ON_CONFLICT(PERSON.NAME)
      .DO_UPDATE
      .SET(PERSON.AGE, PERSON.AGE.EXCLUDED)
      .SET(PERSON.TOKEN, PERSON.TOKEN.EXCLUDED)
      .WHERE(PERSON.AGE.EXCLUDED GREATER_THAN PERSON.AGE)

    val newerAgeSql = """INSERT INTO "main"."person" ("name", "age", "token") VALUES (?, ?, ?) """ +
        """ON CONFLICT ("name") DO UPDATE SET "age" = "excluded"."age", "token" = "excluded"."token" """ +
        """WHERE "excluded"."age" > "person"."age""""
    listOf(
      Case(
        label = "DO UPDATE whose WHERE matches",
        query = newerAge(40),
        plan = PlanSnapshot(
          sql = newerAgeSql,
          arguments = listOf(NativeValue.Text("Ada"), NativeValue.Integer(40), NativeValue.Text("db:x"))
        ),
        outcome = Outcome(
          affected = 1,
          rows = listOf(
            ada.copy(
              age = 40,
              token = Token("x")
            ),
            grace,
            unnamed,
            linus
          ),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "DO UPDATE whose WHERE does not match",
        query = newerAge(30),
        plan = PlanSnapshot(
          sql = newerAgeSql,
          arguments = listOf(NativeValue.Text("Ada"), NativeValue.Integer(30), NativeValue.Text("db:x"))
        ),
        outcome = Outcome(
          affected = 0,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "infix target, SET and WHERE",
        query = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE SET
            (PERSON.AGE to PERSON.AGE + 1) WHERE (PERSON.FLAG IS true),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name") VALUES (?) ON CONFLICT ("name") DO UPDATE """ +
              """SET "age" = "person"."age" + ? WHERE "person"."flag" = ?""",
          arguments = listOf(NativeValue.Text("Ada"), NativeValue.Integer(1), NativeValue.Integer(1))
        ),
        outcome = Outcome(
          affected = 1,
          rows = listOf(ada.copy(age = 37), grace, unnamed, linus),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "composite target with NULL in it",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.PARENT_ID, PERSON.TOKEN)
          .VALUES("Other", 1L, Token("g"))
          .VALUES("Nulled", null, Token("a"))
          .ON_CONFLICT(PERSON.PARENT_ID, PERSON.TOKEN)
          .DO_NOTHING(),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "parent_id", "token") VALUES (?, ?, ?), (?, ?, ?) """ +
              """ON CONFLICT ("parent_id", "token") DO NOTHING""",
          arguments = listOf(
            NativeValue.Text("Other"),
            NativeValue.Integer(1),
            NativeValue.Text("db:g"),
            NativeValue.Text("Nulled"),
            NativeValue.Null,
            NativeValue.Text("db:a")
          )
        ),
        outcome = Outcome(
          affected = 1,
          rows = people + Person(
            id = 5L,
            name = "Nulled",
            age = 7,
            parentId = null,
            token = Token("a"),
            flag = null
          ),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "DO NOTHING without a target",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME)
          .VALUES("Grace")
          .ON_CONFLICT()
          .DO_NOTHING(),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name") VALUES (?) ON CONFLICT DO NOTHING""",
          arguments = listOf(NativeValue.Text("Grace"))
        ),
        outcome = Outcome(
          affected = 0,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "several clauses, the last without a target",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.PARENT_ID, PERSON.TOKEN, PERSON.AGE)
          .VALUES("Ada", null, Token("z"), 50)
          .VALUES("New", 1L, Token("g"), 1)
          .VALUES("Fresh", 7L, Token("f"), 2)
          .ON_CONFLICT(PERSON.NAME)
          .DO_UPDATE
          .SET(PERSON.AGE, PERSON.AGE.EXCLUDED)
          .ON_CONFLICT(PERSON.PARENT_ID, PERSON.TOKEN)
          .DO_NOTHING()
          .ON_CONFLICT()
          .DO_UPDATE
          .SET_NULL(PERSON.FLAG),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "parent_id", "token", "age") """ +
              """VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?) """ +
              """ON CONFLICT ("name") DO UPDATE SET "age" = "excluded"."age" """ +
              """ON CONFLICT ("parent_id", "token") DO NOTHING ON CONFLICT DO UPDATE SET "flag" = NULL""",
          arguments = listOf(
            NativeValue.Text("Ada"),
            NativeValue.Null,
            NativeValue.Text("db:z"),
            NativeValue.Integer(50),
            NativeValue.Text("New"),
            NativeValue.Integer(1),
            NativeValue.Text("db:g"),
            NativeValue.Integer(1),
            NativeValue.Text("Fresh"),
            NativeValue.Integer(7),
            NativeValue.Text("db:f"),
            NativeValue.Integer(2)
          )
        ),
        outcome = Outcome(
          affected = 2,
          rows = listOf(
            ada.copy(age = 50),
            grace,
            unnamed,
            linus,
            Person(
              id = 5L,
              name = "Fresh",
              age = 2,
              parentId = 7L,
              token = Token("f"),
              flag = null
            )
          ),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "EXCLUDED in arithmetic and in a host comparison through the transformer",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.AGE, PERSON.TOKEN)
          .VALUES("Linus", 3, Token("x"))
          .ON_CONFLICT(PERSON.NAME)
          .DO_UPDATE
          .SET(PERSON.AGE, PERSON.AGE.EXCLUDED + PERSON.AGE)
          .WHERE(PERSON.TOKEN.EXCLUDED IS Token("x")),
        plan = PlanSnapshot(
          sql = """INSERT INTO "main"."person" ("name", "age", "token") VALUES (?, ?, ?) ON CONFLICT ("name") """ +
              """DO UPDATE SET "age" = "excluded"."age" + "person"."age" WHERE "excluded"."token" = ?""",
          arguments = listOf(
            NativeValue.Text("Linus"),
            NativeValue.Integer(3),
            NativeValue.Text("db:x"),
            NativeValue.Text("db:x")
          )
        ),
        outcome = Outcome(
          affected = 1,
          rows = listOf(ada, grace, unnamed, linus.copy(age = 20)),
          triggers = listOf(setOf("person"))
        )
      ),
      Case(
        label = "the conflict algorithm handles conflicts outside the target",
        query = INSERT
          .OR(CONFLICT_IGNORE)
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.PARENT_ID, PERSON.TOKEN, PERSON.AGE)
          .VALUES("Zed", 1L, Token("g"), 1)
          .VALUES("Ada", null, Token("q"), 99)
          .ON_CONFLICT(PERSON.NAME)
          .DO_UPDATE
          .SET(PERSON.AGE, PERSON.AGE.EXCLUDED),
        plan = PlanSnapshot(
          sql = """INSERT OR IGNORE INTO "main"."person" ("name", "parent_id", "token", "age") """ +
              """VALUES (?, ?, ?, ?), (?, ?, ?, ?) ON CONFLICT ("name") DO UPDATE SET "age" = "excluded"."age"""",
          arguments = listOf(
            NativeValue.Text("Zed"),
            NativeValue.Integer(1),
            NativeValue.Text("db:g"),
            NativeValue.Integer(1),
            NativeValue.Text("Ada"),
            NativeValue.Null,
            NativeValue.Text("db:q"),
            NativeValue.Integer(99)
          )
        ),
        outcome = Outcome(
          affected = 1,
          rows = listOf(ada.copy(age = 99), grace, unnamed, linus),
          triggers = listOf(setOf("person"))
        )
      )
    )
      .forEach { case ->
        assertWithMessage(case.label)
          .that(snapshot(case.query.compile()))
          .isEqualTo(case.plan)
        assertWithMessage(case.label)
          .that(outcome(case.query))
          .isEqualTo(case.outcome)
      }
  }

  @Test
  fun `INSERT SELECT gets WHERE true before an upsert clause only when its SQL ends in FROM`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, out RowSelection<out Row2<String?, Long?>, *>>,
      val select: String,
      val affected: Int
    )

    val pets = SELECT
      .COLUMNS(PET.NAME, PET.ID)
      .FROM(PET)
    listOf(
      Case(
        label = "single table",
        query = pets,
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" WHERE true""",
        affected = 3
      ),
      Case(
        label = "join",
        query = SELECT
          .COLUMNS(PET.NAME, PET.ID)
          .FROM(PET)
          .INNER_JOIN(PERSON)
          .ON(PET.OWNER IS PERSON.ID),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" INNER JOIN "main"."person" """ +
            """ON "pet"."owner" = "person"."id" WHERE true""",
        affected = 3
      ),
      Case(
        label = "last arm wrapped for its ORDER BY and LIMIT",
        query = SELECT
          .COLUMNS(PET.NAME, PET.ID)
          .FROM(PET)
          .WHERE(PET.ID IS 1L) UNION_ALL SELECT
          .COLUMNS(PET.NAME, PET.ID)
          .FROM(PET)
          .ORDER_BY(PET.ID.DESC())
          .LIMIT(1),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" WHERE "pet"."id" = ? UNION ALL """ +
            """SELECT * FROM (SELECT "pet"."name", "pet"."id" FROM "main"."pet" ORDER BY "pet"."id" DESC """ +
            """LIMIT 1) WHERE true""",
        affected = 2
      ),
      Case(
        label = "derived source",
        query = SELECT
          .COLUMNS(pets[PET.NAME], pets[PET.ID])
          .FROM(pets),
        select = """SELECT "pet.name", "pet.id" FROM (SELECT "pet"."name" AS "pet.name", """ +
            """"pet"."id" AS "pet.id" FROM "main"."pet") WHERE true""",
        affected = 3
      ),
      Case(
        label = "WHERE",
        query = pets.WHERE(PET.ID GREATER_THAN 1L),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" WHERE "pet"."id" > ?""",
        affected = 2
      ),
      Case(
        label = "GROUP BY",
        query = pets.GROUP_BY(PET.NAME),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" GROUP BY "pet"."name"""",
        affected = 3
      ),
      Case(
        label = "HAVING",
        query = pets
          .GROUP_BY(PET.NAME)
          .HAVING(PET.OWNER IS 1L),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" GROUP BY "pet"."name" """ +
            """HAVING "pet"."owner" = ?""",
        affected = 2
      ),
      Case(
        label = "ORDER BY",
        query = pets.ORDER_BY(PET.ID.ASC()),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" ORDER BY "pet"."id" ASC""",
        affected = 3
      ),
      Case(
        label = "LIMIT",
        query = pets.LIMIT(1),
        select = """SELECT "pet"."name", "pet"."id" FROM "main"."pet" LIMIT 1""",
        affected = 1
      ),
      Case(
        label = "no FROM",
        query = SELECT.COLUMNS("Ada".asValue(), 9L.asValue()),
        select = "SELECT ?, ?",
        affected = 1
      )
    )
      .forEach { case ->
        val upsert = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.PARENT_ID)
          .SELECT(case.query)
          .ON_CONFLICT(PERSON.NAME)
          .DO_UPDATE
          .SET(PERSON.PARENT_ID, PERSON.PARENT_ID.EXCLUDED)
        assertWithMessage(case.label)
          .that(listOf(upsert.compile().sql, outcome(upsert).affected))
          .isEqualTo(
            listOf(
              """INSERT INTO "main"."person" ("name", "parent_id") ${case.select} """ +
                  """ON CONFLICT ("name") DO UPDATE SET "parent_id" = "excluded"."parent_id"""",
              case.affected
            )
          )
      }
  }

  @Test
  fun `INSERT OR REPLACE with an upsert clause notifies the tables its deletes cascade into`() {
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf(
          "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT UNIQUE, age INTEGER NOT NULL DEFAULT 7, " +
              "parent_id INTEGER, token TEXT NOT NULL, flag INTEGER, UNIQUE (parent_id, token))",
          "INSERT INTO person VALUES (1, 'Ada', 36, NULL, 'db:a', 1), (2, 'Grace', 85, 1, 'db:g', 0)"
        ),
        cascadeTables = mapOf("person" to arrayOf("person", "pet"))
      ),
      driver = BundledSQLiteDriver()
    )
    val recorder = connection.recordTriggers()
    val replaced = INSERT
      .OR(CONFLICT_REPLACE)
      .INTO(PERSON)
      .COLUMNS(PERSON.NAME, PERSON.PARENT_ID, PERSON.TOKEN)
      .VALUES("Zed", 1L, Token("g"))
      .ON_CONFLICT(PERSON.NAME)
      .DO_NOTHING()

    try {
      assertThat(
        replaced
          .usingConnection(connection)
          .execute()
      )
        .isEqualTo(1)
      assertThat(recorder.triggers).isEqualTo(listOf(setOf("person", "pet")))
      assertThat(connection.persons())
        .isEqualTo(
          listOf(
            ada,
            Person(
              id = 3L,
              name = "Zed",
              age = 7,
              parentId = 1L,
              token = Token("g"),
              flag = null
            )
          )
        )
    } finally {
      connection.pool.close()
    }
  }

  @Test
  fun `DO UPDATE fails on a NOT NULL violation whatever the conflict algorithm`() {
    listOf(INSERT.INTO(PERSON), INSERT.OR(CONFLICT_IGNORE).INTO(PERSON), INSERT.OR(CONFLICT_REPLACE).INTO(PERSON))
      .map { insert ->
        outcome(
          insert
            .COLUMNS(PERSON.NAME)
            .VALUES("Fresh")
            .VALUES("Ada")
            .ON_CONFLICT(PERSON.NAME)
            .DO_UPDATE
            .SET_NULL(PERSON.AGE)
        )
      }
      .forEach { outcome ->
        assertThat(outcome)
          .isEqualTo(
            Outcome(
              affected = null,
              rows = people,
              triggers = listOf(setOf("person")),
              failure = "NOT NULL constraint failed: person.age"
            )
          )
      }
  }

  @Test
  fun `readRowId survives DO NOTHING clauses`() {
    data class Case(
      val label: String,
      val query: RowIdInsertQuery,
      val rowId: Long?
    )

    listOf(
      Case(
        label = "inserted",
        query = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Fresh" ON_CONFLICT PERSON.NAME)
          .DO_NOTHING()
          .readRowId(),
        rowId = 5L
      ),
      Case(
        label = "skipped",
        query = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME)
          .DO_NOTHING()
          .readRowId(),
        rowId = null
      ),
      Case(
        label = "skipped by a later clause without a target",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.NAME, PERSON.PARENT_ID, PERSON.TOKEN)
          .VALUES("Zed", 1L, Token("g"))
          .ON_CONFLICT(PERSON.NAME)
          .DO_NOTHING()
          .ON_CONFLICT()
          .DO_NOTHING()
          .readRowId(),
        rowId = null
      ),
      Case(
        label = "wide row inserted",
        query = INSERT
          .INTO(PERSON)
          .COLUMNS(*arrayOf(PERSON.NAME, PERSON.AGE))
          .VALUES {
            it[PERSON.NAME] = "Fresh"
            it[PERSON.AGE] = 1
          }
          .ON_CONFLICT(PERSON.NAME)
          .DO_NOTHING()
          .readRowId(),
        rowId = 5L
      )
    )
      .forEach { case ->
        upsertDatabase().use { database ->
          val recorder = database.connection.recordTriggers()
          val rowId = case.query
            .usingConnection(database.connection)
            .execute()

          assertWithMessage(case.label)
            .that(listOf(rowId, recorder.triggers))
            .isEqualTo(
              listOf(
                case.rowId,
                when (case.rowId) {
                  null -> emptyList()
                  else -> listOf(setOf("person"))
                }
              )
            )
        }
      }
  }

  @Test
  fun `targets, SET targets and fields outside the inserted table reject before SQL`() {
    val alias = PERSON.AS("p")
    val connection = mock<DbConnectionImpl>()
    val update = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE
    val missingOccurrence = "A field requires the exact table occurrence of its FROM, UPDATE, DELETE or INSERT"

    assertThat(
      listOf(
        { INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT alias.NAME },
        { update SET (alias.AGE to 1) },
        {
          update
            .SET(PERSON.NAME, PET.NAME)
            .usingConnection(connection)
            .execute()
        },
        {
          (update SET (PERSON.AGE to 1) WHERE (PET.ID IS 1L))
            .usingConnection(connection)
            .execute()
        },
        {
          update
            .SET(PERSON.NAME, PET.NAME.EXCLUDED)
            .usingConnection(connection)
            .execute()
        },
        {
          (update SET (PERSON.AGE to 1) WHERE (alias.AGE.EXCLUDED IS 1))
            .usingConnection(connection)
            .execute()
        },
        {
          (INSERT INTO PERSON COLUMNS PERSON.AGE VALUES PERSON.AGE ON_CONFLICT PERSON.NAME)
            .DO_NOTHING()
            .usingConnection(connection)
            .execute()
        },
        {
          (INSERT INTO PERSON COLUMNS PERSON.NAME SELECT (SELECT COLUMN PERSON.NAME FROM PET) ON_CONFLICT PERSON.NAME)
            .DO_NOTHING()
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
          "ON CONFLICT column name must be a field of the inserted person occurrence",
          "DO UPDATE SET target age must be a field of the inserted person occurrence",
          missingOccurrence,
          missingOccurrence,
          "EXCLUDED column name must be a field of the inserted person occurrence",
          "EXCLUDED column age must be a field of the inserted person occurrence",
          "INSERT VALUES cannot read a field outside a subquery",
          missingOccurrence
        )
      )
    verifyNoInteractions(connection)
  }

  @Test
  fun `an empty list runs no SQL even when SQLite would reject its upsert target`() {
    val recording = newConnection()
    val target = PERSON.AGE
    val empty = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.AGE)
      .VALUES(emptyList())
      .ON_CONFLICT(target)
      .DO_NOTHING()
      .usingConnection(recording.dbConnection)

    assertThat(listOf(empty.compile().sql, empty.execute()))
      .isEqualTo(
        listOf(
          """INSERT INTO "main"."person" ("age") SELECT NULL WHERE 0 ON CONFLICT ("age") DO NOTHING""",
          0
        )
      )
    assertThat(recording.sqliteConnection.compiledStatements).isEmpty()
    assertThat(recording.triggers).isEmpty()
    assertThat(
      outcome(
        INSERT
          .INTO(PERSON)
          .COLUMNS(PERSON.AGE)
          .VALUES(listOf(Row1(1)))
          .ON_CONFLICT(target)
          .DO_NOTHING()
      )
        .failure
    )
      .isEqualTo("ON CONFLICT clause does not match any PRIMARY KEY or UNIQUE constraint")
  }

  @Test
  fun `upsert statements with fixed rows prepare through the statement cache`() {
    val recording = newConnection()
    val compiled = INSERT
      .INTO(PERSON)
      .COLUMNS(PERSON.NAME, PERSON.AGE)
      .VALUES("Ada", 1)
      .ON_CONFLICT(PERSON.NAME)
      .DO_UPDATE
      .SET(PERSON.AGE, PERSON.AGE.EXCLUDED)
      .usingConnection(recording.dbConnection)
      .compile()

    assertThat(List(2) { compiled.execute() }).isEqualTo(listOf(1, 1))
    assertThat(recording.sqliteConnection.compiledStatements.map { it.sql to it.closeCalls })
      .isEqualTo(
        listOf(
          """INSERT INTO "main"."person" ("name", "age") VALUES (?, ?) """ +
              """ON CONFLICT ("name") DO UPDATE SET "age" = "excluded"."age"""" to 0
        )
      )
  }
}
