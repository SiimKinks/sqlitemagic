package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

private data class Event(
  val id: Long,
  val kind: String,
  val at: Long,
  val userId: Long?,
  val token: Token,
  val screen: String?,
  val x: Int,
  val y: Int,
  val flag: Boolean?,
  val os: String,
  val osVersion: Int?
)

@Suppress("PropertyName")
private class EventTable(alias: String? = null) : Table<Event>(
  name = "event",
  alias = alias
) {
  val ID = UniqueColumn<Event, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val KIND = Column<Event, String, Required, String, String, Required, String>(
    table = this,
    name = "kind",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val AT = Column<Event, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "at",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val USER_ID = Column(
    table = this,
    name = "user_id",
    nullness = SqlNullable,
    parameterEncoding = LONG.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getLong)
  )
  val TOKEN = Column<Event, Token, Required, Token, Token, Required, Token>(
    table = this,
    name = "token",
    nullness = Required,
    parameterEncoding = TOKEN_TYPE.parameterEncoding()
  )
  val SCREEN = Column(
    table = this,
    name = "screen",
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getText)
  )
  val X = Column<Event, Int, Required, Int, Int, Required, Int>(
    table = this,
    name = "x",
    nullness = Required,
    parameterEncoding = INT.parameterEncoding()
  )
  val Y = Column<Event, Int, Required, Int, Int, Required, Int>(
    table = this,
    name = "y",
    nullness = Required,
    parameterEncoding = INT.parameterEncoding()
  )
  val FLAG = Column(
    table = this,
    name = "flag",
    nullness = SqlNullable,
    parameterEncoding = BOOLEAN.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getBoolean)
  )
  val OS = Column<Event, String, Required, String, String, Required, String>(
    table = this,
    name = "os",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val OS_VERSION = Column(
    table = this,
    name = "os_version",
    nullness = SqlNullable,
    parameterEncoding = INT.nullBypassParameterEncoding(),
    selectedRead = nullableRead(SQLiteStatement::getInt)
  )

  override val entityFields = listOf(ID, KIND, AT, USER_ID, TOKEN, SCREEN, X, Y, FLAG, OS, OS_VERSION)

  override fun readEntity(statement: SQLiteStatement, startIndex: Int, related: RelatedEntities?) = Event(
    id = ID.selectRead(statement, startIndex),
    kind = KIND.selectRead(statement, startIndex + 1),
    at = AT.selectRead(statement, startIndex + 2),
    userId = USER_ID.selectRead(statement, startIndex + 3),
    token = TOKEN.selectRead(statement, startIndex + 4),
    screen = SCREEN.selectRead(statement, startIndex + 5),
    x = X.selectRead(statement, startIndex + 6),
    y = Y.selectRead(statement, startIndex + 7),
    flag = FLAG.selectRead(statement, startIndex + 8),
    os = OS.selectRead(statement, startIndex + 9),
    osVersion = OS_VERSION.selectRead(statement, startIndex + 10)
  )

  @Suppress("TestFunctionName")
  override infix fun AS(alias: String) = EventTable(alias)
}

private val EVENT = EventTable()

private const val EVENT_SCHEMA = "CREATE TABLE event (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, " +
    "at INTEGER NOT NULL, user_id INTEGER, token TEXT NOT NULL, screen TEXT, x INTEGER NOT NULL, " +
    "y INTEGER NOT NULL, flag INTEGER, os TEXT NOT NULL, os_version INTEGER)"

private val listed = arrayOf(
  EVENT.KIND,
  EVENT.AT,
  EVENT.USER_ID,
  EVENT.TOKEN,
  EVENT.SCREEN,
  EVENT.X,
  EVENT.Y,
  EVENT.FLAG,
  EVENT.OS,
  EVENT.OS_VERSION
)

private const val WIDE_COLUMNS =
    """("kind", "at", "user_id", "token", "screen", "x", "y", "flag", "os", "os_version")"""

private const val WIDE_ROW = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

private val tap = Event(
  id = 1L,
  kind = "tap",
  at = 100L,
  userId = null,
  token = Token("t"),
  screen = "home",
  x = 1,
  y = 2,
  flag = true,
  os = "android",
  osVersion = null
)

private val swipe = Event(
  id = 2L,
  kind = "swipe",
  at = 200L,
  userId = 4L,
  token = Token("s"),
  screen = null,
  x = 3,
  y = 4,
  flag = null,
  os = "ios",
  osVersion = 17
)

private fun InsertedRow<Event>.assign(event: Event) {
  this[EVENT.KIND] = event.kind
  this[EVENT.AT] = event.at
  this[EVENT.USER_ID] = event.userId
  this[EVENT.TOKEN] = event.token
  this[EVENT.SCREEN] = event.screen
  this[EVENT.X] = event.x
  this[EVENT.Y] = event.y
  this[EVENT.FLAG] = event.flag
  this[EVENT.OS] = event.os
  this[EVENT.OS_VERSION] = event.osVersion
}

private fun eventDatabase() = personDatabase().apply { execSQL(EVENT_SCHEMA) }

private fun DbConnection.events() = SELECT.FROM(EVENT)
  .ORDER_BY(EVENT.ID.ASC())
  .usingConnection(this)
  .execute()

internal class WideInsertStatementTest {
  private data class Outcome(
    val affected: Int?,
    val rows: List<Event>,
    val triggers: List<Set<String>>
  )

  private fun outcome(query: MutationQuery) = eventDatabase().use { database ->
    val connection = database.connection
    val recorder = connection.recordTriggers()
    val affected = runCatching {
      query
        .usingConnection(connection)
        .execute()
    }
    Outcome(
      affected = affected.getOrNull(),
      rows = connection.events(),
      triggers = recorder.triggers
    )
  }

  @Test
  fun `wide rows render one VALUES clause and insert into the listed columns`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val sql: String,
      val rows: List<Event>
    )

    listOf(
      Case(
        label = "fluent row",
        query = INSERT
          .INTO(EVENT)
          .COLUMNS(
            EVENT.KIND,
            EVENT.AT,
            EVENT.USER_ID,
            EVENT.TOKEN,
            EVENT.SCREEN,
            EVENT.X,
            EVENT.Y,
            EVENT.FLAG,
            EVENT.OS,
            EVENT.OS_VERSION
          )
          .VALUES { it.assign(tap) },
        sql = """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW""",
        rows = listOf(tap)
      ),
      Case(
        label = "infix rows of spread columns",
        query = INSERT
          .INTO(EVENT)
          .COLUMNS(*listed) VALUES { it.assign(tap) } VALUES { it.assign(swipe) },
        sql = """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW, $WIDE_ROW""",
        rows = listOf(tap, swipe)
      ),
      Case(
        label = "list rows after a row and between empty lists",
        query = INSERT
          .INTO(EVENT)
          .COLUMNS(*listed)
          .VALUES(emptyList(), InsertedRow<Event>::assign)
          .VALUES { it.assign(tap) }
          .VALUES(listOf(swipe, tap.copy(id = 3L)), InsertedRow<Event>::assign)
          .VALUES(emptyList<Event>()) { row, event -> row.assign(event.copy(kind = "never")) },
        sql = """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW, $WIDE_ROW, $WIDE_ROW""",
        rows = listOf(tap, swipe, tap.copy(id = 3L))
      ),
      Case(
        label = "conflict algorithm",
        query = INSERT
          .OR(CONFLICT_IGNORE)
          .INTO(EVENT)
          .COLUMNS(*listed)
          .VALUES { it.assign(tap) },
        sql = """INSERT OR IGNORE INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW""",
        rows = listOf(tap)
      )
    )
      .forEach { case ->
        assertWithMessage(case.label)
          .that(case.query.compile().sql)
          .isEqualTo(case.sql)
        assertWithMessage(case.label)
          .that(outcome(case.query))
          .isEqualTo(
            Outcome(
              affected = case.rows.size,
              rows = case.rows,
              triggers = listOf(setOf("event"))
            )
          )
      }
  }

  @Test
  fun `wide host values bind in column order through the column encoders`() {
    val insert = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)
      .VALUES {
        it[EVENT.OS_VERSION] = 17
        it.assign(tap)
      }

    assertThat(snapshot(insert.compile()))
      .isEqualTo(
        PlanSnapshot(
          sql = """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW""",
          arguments = listOf(
            NativeValue.Text("tap"),
            NativeValue.Integer(100),
            NativeValue.Null,
            NativeValue.Text("db:t"),
            NativeValue.Text("home"),
            NativeValue.Integer(1),
            NativeValue.Integer(2),
            NativeValue.Integer(1),
            NativeValue.Text("android"),
            NativeValue.Null
          )
        )
      )
  }

  @Test
  fun `wide rows take expressions, and a repeated column takes its last value`() {
    val screen = EVENT.SCREEN.asParameter("screen")
    fun parentOf(id: Long) = SELECT
      .COLUMN(PERSON.PARENT_ID)
      .FROM(PERSON)
      .WHERE(PERSON.ID IS id)
      .asScalar()
    val people = SELECT
      .COLUMN(COUNT())
      .FROM(PERSON)
      .asScalar()
      .readAs(INT)
    val insert = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)
      .VALUES {
        it.assign(tap)
        it[EVENT.KIND] = "lower(?)".expr(STRING, "TAP")
        it[EVENT.USER_ID] = parentOf(3L)
        it[EVENT.SCREEN] = screen
        it[EVENT.X] = people
        it[EVENT.Y] = (40.asValue() + 2).readAs(INT)
        it[EVENT.TOKEN] = Token("e").asValue(TOKEN_TYPE)
        it[EVENT.OS] = "web".asValue()
        it[EVENT.OS] = "linux"
      }
      .VALUES {
        it[EVENT.USER_ID] = 7L
        it[EVENT.FLAG] = false.asValue()
        it.assign(swipe)
        it[EVENT.USER_ID] = parentOf(99L)
      }
      .bind(screen TO "feed")

    assertThat(insert.compile().sql)
      .isEqualTo(
        """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES """ +
            """(lower(?), ?, (SELECT "person"."parent_id" FROM "main"."person" WHERE "person"."id" = ?), ?, ?, """ +
            """(SELECT COUNT(*) FROM "main"."person"), ? + ?, ?, ?, ?), """ +
            """(?, ?, (SELECT "person"."parent_id" FROM "main"."person" WHERE "person"."id" = ?), """ +
            """?, ?, ?, ?, ?, ?, ?)"""
      )
    assertThat(outcome(insert))
      .isEqualTo(
        Outcome(
          affected = 2,
          rows = listOf(
            tap.copy(
              userId = 2L,
              token = Token("e"),
              screen = "feed",
              x = 4,
              y = 42,
              os = "linux"
            ),
            swipe.copy(userId = null)
          ),
          triggers = listOf(setOf("event"))
        )
      )
  }

  @Test
  fun `nullable scalar subqueries in wide rows leave NOT NULL to SQLite`() {
    val insert = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)
      .VALUES {
        it.assign(tap)
        it[EVENT.AT] = SELECT
          .COLUMN(PERSON.PARENT_ID)
          .FROM(PERSON)
          .WHERE(PERSON.ID IS 1L)
          .asScalar()
      }

    assertThat(outcome(insert))
      .isEqualTo(
        Outcome(
          affected = null,
          rows = emptyList(),
          triggers = listOf(setOf("event"))
        )
      )
    eventDatabase().use { database ->
      assertThat(
        sqliteErrorMessage {
          insert
            .usingConnection(database.connection)
            .execute()
        }
      )
        .isEqualTo("NOT NULL constraint failed: event.at")
    }
  }

  @Test
  fun `missing, unlisted and bare fields reject before SQL`() {
    val alias = EVENT.AS("e")
    val connection = mock<DbConnectionImpl>()
    val columns = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)

    assertThat(
      listOf(
        { columns.VALUES { it[EVENT.KIND] = "tap" } },
        {
          columns.VALUES {
            it.assign(tap)
            it[EVENT.ID] = 1L
          }
        },
        {
          columns.VALUES {
            it.assign(tap)
            it[alias.KIND] = "tap"
          }
        },
        {
          columns.VALUES(listOf(tap, swipe)) { row, event ->
            if (event == tap) {
              row.assign(event)
            }
          }
        },
        { INSERT.INTO(EVENT).COLUMNS(*listed.sliceArray(0..8), alias.OS_VERSION) },
        {
          columns
            .VALUES {
              it.assign(tap)
              it[EVENT.X] = EVENT.Y
            }
            .usingConnection(connection)
            .execute()
        },
        {
          columns
            .VALUES {
              it.assign(tap)
              it[EVENT.KIND] = PERSON.NAME
            }
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
          "INSERT row assigns no value to column at",
          "INSERT column id is not listed in COLUMNS",
          "INSERT column kind must be a field of the inserted event occurrence",
          "INSERT row assigns no value to column kind",
          "INSERT column os_version must be a field of the inserted event occurrence",
          "INSERT VALUES cannot read a field outside a subquery",
          "INSERT VALUES cannot read a field outside a subquery"
        )
      )
    verifyNoInteractions(connection)
  }

  @Test
  fun `columns listed twice or not at all are left to SQLite`() {
    val twice = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed, EVENT.KIND)
      .VALUES {
        it.assign(tap)
        it[EVENT.KIND] = "first"
      }
    val none = INSERT
      .INTO(EVENT)
      .COLUMNS()
      .VALUES {}

    assertThat(snapshot(twice.compile()).arguments.let { listOf(it.first(), it.last()) })
      .isEqualTo(listOf(NativeValue.Text("first"), NativeValue.Text("first")))
    assertThat(outcome(twice).rows).isEqualTo(listOf(tap.copy(kind = "first")))
    assertThat(none.compile().sql).isEqualTo("""INSERT INTO "main"."event" () VALUES ()""")
    eventDatabase().use { database ->
      assertThat(
        sqliteErrorMessage {
          none
            .usingConnection(database.connection)
            .execute()
        }
      )
        .isEqualTo("""near ")": syntax error""")
    }
  }

  @Test
  fun `wide list rows prepare outside the statement cache and other rows use it`() {
    val recording = newConnection()
    val listedRows = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)
      .VALUES(listOf(tap), InsertedRow<Event>::assign)
      .usingConnection(recording.dbConnection)
      .compile()
    val fixed = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)
      .VALUES { it.assign(tap) }
      .usingConnection(recording.dbConnection)
      .compile()

    assertThat(List(2) { listedRows.execute() } + List(2) { fixed.execute() }).isEqualTo(List(4) { 1 })
    assertThat(recording.sqliteConnection.compiledStatements.map { it.sql to it.closeCalls })
      .isEqualTo(
        listOf(
          """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW""" to 1,
          """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW""" to 1,
          """INSERT INTO "main"."event" $WIDE_COLUMNS VALUES $WIDE_ROW""" to 0
        )
      )
  }

  @Test
  fun `wide statements whose rows all come from empty lists run no SQL`() {
    val recording = newConnection()
    val empty = INSERT
      .INTO(EVENT)
      .COLUMNS(*listed)
      .VALUES(emptyList(), InsertedRow<Event>::assign)
      .usingConnection(recording.dbConnection)

    assertThat(snapshot(empty.compile()))
      .isEqualTo(
        PlanSnapshot(
          sql = """INSERT INTO "main"."event" $WIDE_COLUMNS """ +
              "SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL WHERE 0",
          arguments = emptyList()
        )
      )
    assertThat(listOf(empty.execute(), empty.compile().execute())).isEqualTo(listOf(0, 0))
    assertThat(recording.sqliteConnection.compiledStatements).isEmpty()
    assertThat(recording.triggers).isEmpty()
  }

  @Test
  fun `wide counts follow changes and REPLACE notifies the tables its deletes cascade into`() {
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf(EVENT_SCHEMA),
        cascadeTables = mapOf("event" to arrayOf("event", "person"))
      )
    )
    val recorder = connection.recordTriggers()
    fun insert(conflictAlgorithm: Int, event: Event) = INSERT
      .OR(conflictAlgorithm)
      .INTO(EVENT)
      .COLUMNS(EVENT.ID, *listed)
      .VALUES {
        it[EVENT.ID] = event.id
        it.assign(event)
      }
      .usingConnection(connection)
      .execute()

    try {
      val affected = listOf(
        insert(
          conflictAlgorithm = CONFLICT_IGNORE,
          event = tap
        ),
        insert(
          conflictAlgorithm = CONFLICT_IGNORE,
          event = swipe.copy(id = 1L)
        ),
        insert(
          conflictAlgorithm = CONFLICT_REPLACE,
          event = swipe.copy(id = 1L)
        )
      )

      assertThat(affected).isEqualTo(listOf(1, 0, 1))
      assertThat(recorder.triggers).isEqualTo(listOf(setOf("event"), setOf("event", "person")))
      assertThat(connection.events()).isEqualTo(listOf(swipe.copy(id = 1L)))
    } finally {
      connection.pool.close()
    }
  }
}
