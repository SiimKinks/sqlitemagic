package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.requiredReadType
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class MutationStatementTest {
  private data class Outcome(
    val affected: Int?,
    val rows: List<Person>,
    val triggers: List<Set<String>>
  )

  private fun outcome(
    query: MutationQuery,
    database: BundledSqliteDatabase = personDatabase()
  ) = database.use {
    val connection = database.dbConnection()
    val triggers = connection.recordTriggers()
    val affected = runCatching {
      query
        .usingConnection(connection)
        .execute()
    }
    Outcome(
      affected = affected.getOrNull(),
      rows = connection.persons(),
      triggers = triggers.values()
    )
  }

  @Test
  fun `UPDATE writes its assignments to the matching rows and returns the affected count`() {
    val minors = UPDATE TABLE PERSON SET (PERSON.NAME to "Minor") SET (PERSON.AGE to PERSON.AGE + 1) WHERE
        (PERSON.AGE LESS_THAN 18)

    assertThat(snapshot(minors.compile())).isEqualTo(
      PlanSnapshot(
        sql = """UPDATE "main"."person" SET "name" = ?, "age" = "person"."age" + ? """ +
            """WHERE "person"."age" < ?""",
        arguments = listOf(NativeValue.Text("Minor"), NativeValue.Integer(1), NativeValue.Integer(18))
      )
    )
    assertThat(outcome(minors)).isEqualTo(
      Outcome(
        affected = 2,
        rows = listOf(
          ada,
          grace,
          unnamed.copy(
            name = "Minor",
            age = 18
          ),
          linus.copy(
            name = "Minor",
            age = 18
          )
        ),
        triggers = listOf(setOf("person"))
      )
    )
  }

  @Test
  fun `statements without WHERE affect every row and WHERE limits them`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val sql: String,
      val affected: Int,
      val rows: List<Person>
    )

    listOf(
      Case(
        label = "UPDATE without WHERE",
        query = UPDATE TABLE PERSON SET (PERSON.FLAG to false),
        sql = """UPDATE "main"."person" SET "flag" = ?""",
        affected = 4,
        rows = people.map { it.copy(flag = false) }
      ),
      Case(
        label = "DELETE without WHERE",
        query = DELETE FROM PERSON,
        sql = """DELETE FROM "main"."person"""",
        affected = 4,
        rows = emptyList()
      ),
      Case(
        label = "DELETE with WHERE",
        query = (DELETE FROM PERSON) WHERE (PERSON.AGE LESS_THAN 18),
        sql = """DELETE FROM "main"."person" WHERE "person"."age" < ?""",
        affected = 2,
        rows = listOf(ada, grace)
      ),
      Case(
        label = "DELETE with raw WHERE",
        query = (DELETE FROM PERSON) WHERE ("age < ?".expr(BOOLEAN, 18)),
        sql = """DELETE FROM "main"."person" WHERE age < ?""",
        affected = 2,
        rows = listOf(ada, grace)
      )
    ).forEach { case ->
      assertWithMessage(case.label)
        .that(case.query.compile().sql)
        .isEqualTo(case.sql)
      assertWithMessage(case.label)
        .that(outcome(case.query))
        .isEqualTo(
          Outcome(
            affected = case.affected,
            rows = case.rows,
            triggers = listOf(setOf("person"))
          )
        )
    }
  }

  @Test
  fun `host values bind natively through the target encoder`() {
    val update = UPDATE TABLE PERSON SET (PERSON.AGE to 40) SET (PERSON.TOKEN to Token("x")) SET
        (PERSON.FLAG to false) SET (PERSON.NAME to null) WHERE (PERSON.ID IS 1L)

    assertThat(snapshot(update.compile())).isEqualTo(
      PlanSnapshot(
        sql = """UPDATE "main"."person" SET "age" = ?, "token" = ?, "flag" = ?, "name" = ? """ +
            """WHERE "person"."id" = ?""",
        arguments = listOf(
          NativeValue.Integer(40),
          NativeValue.Text("db:x"),
          NativeValue.Integer(0),
          NativeValue.Null,
          NativeValue.Integer(1)
        )
      )
    )
    personDatabase().use { database ->
      update
        .usingConnection(database.dbConnection())
        .execute()

      val stored = database
        .query("SELECT typeof(age), token, typeof(flag), flag, name IS NULL FROM person WHERE id = 1")
        .use { cursor ->
          cursor.moveToFirst()
          listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3), cursor.getLong(4))
        }
      assertThat(stored).isEqualTo(listOf("integer", "db:x", "integer", 0L, 1L))
    }
  }

  @Test
  fun `custom numeric encoders encode assignments and arguments follow SQL order`() {
    data class AccountId(val value: Long)
    class Article

    val accountType = requiredReadType(
      storage = SqlStorage.LONG,
      encode = { id: AccountId -> id.value + 1L },
      decode = { stored -> AccountId(stored - 1L) },
      encodingNullness = Required
    )
    val article = object : Table<Article>(
      name = "article",
      alias = null
    ) {
      val TITLE = Column<Article, String, Required, String, String, Required, String>(
        table = this,
        name = "title",
        nullness = Required,
        parameterEncoding = STRING.parameterEncoding()
      )
      val ACCOUNT = Column<Article, AccountId, Required, AccountId, AccountId, Required, AccountId>(
        table = this,
        name = "account",
        nullness = Required,
        parameterEncoding = accountType.parameterEncoding()
      )
    }
    val update = UPDATE TABLE article SET (article.TITLE to "Title") SET (article.ACCOUNT to AccountId(41L)) SET
        (article.ACCOUNT to "? + account".expr(accountType, 7L))

    assertThat(snapshot(update.compile())).isEqualTo(
      PlanSnapshot(
        sql = """UPDATE "main"."article" SET "title" = ?, "account" = ?, "account" = ? + account""",
        arguments = listOf(NativeValue.Text("Title"), NativeValue.Integer(42), NativeValue.Integer(7))
      )
    )
    BundledSqliteDatabase().use { database ->
      database.execSQL("CREATE TABLE article (title TEXT NOT NULL, account INTEGER NOT NULL)")
      database.execSQL("INSERT INTO article VALUES ('Draft', 1)")
      update
        .usingConnection(database.dbConnection())
        .execute()

      val stored = database
        .query("SELECT title, account FROM article")
        .use { cursor ->
          cursor.moveToFirst()
          listOf(cursor.getString(0), cursor.getLong(1))
        }
      assertThat(stored).isEqualTo(listOf("Title", 8L))
    }
  }

  @Test
  fun `SQL NULL assignment leaves NOT NULL and conflict handling to SQLite`() {
    val defaultedAge = BundledSqliteDatabase().apply {
      execSQL(
        "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT, age INTEGER NOT NULL DEFAULT 7, " +
            "parent_id INTEGER, token TEXT NOT NULL, flag INTEGER)"
      )
      execSQL("INSERT INTO person VALUES (1, 'Ada', 36, NULL, 'db:a', 1)")
    }
    val nullableTarget = UPDATE TABLE PERSON SET_NULL PERSON.NAME WHERE (PERSON.ID IS 1L)
    val ignored = UPDATE OR CONFLICT_IGNORE TABLE PERSON SET_NULL PERSON.AGE WHERE (PERSON.ID IS 1L)
    val replaced = UPDATE OR CONFLICT_REPLACE TABLE PERSON SET_NULL PERSON.AGE WHERE (PERSON.ID IS 1L)

    assertThat(listOf(nullableTarget, ignored, replaced).map { it.compile().sql }).isEqualTo(
      listOf(
        """UPDATE "main"."person" SET "name" = NULL WHERE "person"."id" = ?""",
        """UPDATE OR IGNORE "main"."person" SET "age" = NULL WHERE "person"."id" = ?""",
        """UPDATE OR REPLACE "main"."person" SET "age" = NULL WHERE "person"."id" = ?"""
      )
    )
    assertThat(
      listOf(
        outcome(nullableTarget),
        outcome(ignored),
        outcome(
          query = replaced,
          database = defaultedAge
        )
      )
    ).isEqualTo(
      listOf(
        Outcome(
          affected = 1,
          rows = listOf(ada.copy(name = null), grace, unnamed, linus),
          triggers = listOf(setOf("person"))
        ),
        Outcome(
          affected = 0,
          rows = people,
          triggers = emptyList()
        ),
        Outcome(
          affected = 1,
          rows = listOf(ada.copy(age = 7)),
          triggers = listOf(setOf("person"))
        )
      )
    )
  }

  @Test
  fun `expression values read the row being updated`() {
    val update = UPDATE TABLE PERSON SET (PERSON.PARENT_ID to PERSON.AGE / 2) SET
        (PERSON.AGE to "abs(age) * 2".expr(INT)) WHERE (PERSON.ID IS 2L)

    assertThat(update.compile().sql).isEqualTo(
      """UPDATE "main"."person" SET "parent_id" = "person"."age" / ?, "age" = abs(age) * 2 """ +
          """WHERE "person"."id" = ?"""
    )
    assertThat(outcome(update)).isEqualTo(
      Outcome(
        affected = 1,
        rows = listOf(
          ada,
          grace.copy(
            parentId = 42L,
            age = 170
          ),
          unnamed,
          linus
        ),
        triggers = listOf(setOf("person"))
      )
    )
  }

  @Test
  fun `parameters bind on the query before compilation`() {
    val name = PERSON.NAME.asParameter("name")
    val other = PERSON.AGE.asParameter("age")
    val rename = UPDATE TABLE PERSON SET (PERSON.NAME to name) WHERE (PERSON.ID IS 1L)

    assertThat(
      listOf(
        rename.bind(name TO "Augusta"),
        rename
          .bind(name TO "Augusta")
          .bind(name TO "Ada King")
      ).map(::outcome)
    ).isEqualTo(
      listOf("Augusta", "Ada King").map { newName ->
        Outcome(
          affected = 1,
          rows = listOf(ada.copy(name = newName), grace, unnamed, linus),
          triggers = listOf(setOf("person"))
        )
      }
    )
    assertThat(
      listOf(rename, rename.bind(name TO "A", other TO 1)).map { query ->
        assertThrows(IllegalArgumentException::class.java, query::compile).message
      }
    ).isEqualTo(listOf("Missing parameter 'name'", "Unexpected parameter 'age'"))
  }

  @Test
  fun `aliased targets and foreign occurrences reject before SQL`() {
    val alias = PERSON.AS("p")
    val connection = mock<DbConnectionImpl>()

    assertThat(
      listOf(
        { UPDATE TABLE alias },
        { DELETE FROM alias },
        { UPDATE TABLE PERSON SET (alias.NAME to "Ada") },
        {
          ((DELETE FROM PERSON) WHERE (alias.AGE LESS_THAN 18))
            .usingConnection(connection)
            .execute()
        }
      ).map { construction ->
        assertThrows(IllegalArgumentException::class.java) { construction() }.message
      }
    ).isEqualTo(
      listOf(
        "UPDATE and DELETE targets cannot be aliased; use person itself and alias inner queries instead",
        "UPDATE and DELETE targets cannot be aliased; use person itself and alias inner queries instead",
        "SET target name must be a field of the updated person occurrence",
        "A field requires the exact table occurrence of its FROM, UPDATE or DELETE"
      )
    )
    verifyNoInteractions(connection)
  }

  @Test
  fun `only failures after native execution starts invalidate`() {
    data class Case(
      val label: String,
      val query: MutationQuery,
      val outcome: Outcome
    )

    listOf(
      Case(
        label = "no matching rows",
        query = UPDATE TABLE PERSON SET (PERSON.NAME to "Nobody") WHERE (PERSON.ID IS 99L),
        outcome = Outcome(
          affected = 0,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "invalid SQL fails when the statement compiles",
        query = (DELETE FROM PERSON) WHERE ("no_such_column < 1".expr(BOOLEAN)),
        outcome = Outcome(
          affected = null,
          rows = people,
          triggers = emptyList()
        )
      ),
      Case(
        label = "constraint failure after execution starts",
        query = UPDATE TABLE PERSON SET_NULL PERSON.TOKEN,
        outcome = Outcome(
          affected = null,
          rows = people,
          triggers = listOf(setOf("person"))
        )
      )
    ).forEach { case ->
      assertWithMessage(case.label)
        .that(outcome(case.query))
        .isEqualTo(case.outcome)
    }
  }

  @Test
  fun `observe is cold and executes on every subscription`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val birthday = (UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.AGE + 1) WHERE (PERSON.ID IS 1L))
        .usingConnection(connection)
        .observe()

      assertThat(connection.persons()).isEqualTo(people)
      birthday
        .test()
        .assertResult(1)
      birthday
        .test()
        .assertResult(1)
      assertThat(connection.persons()).isEqualTo(listOf(ada.copy(age = 38), grace, unnamed, linus))
    }
  }

  @Test
  fun `stages branch into independent statements`() {
    val renamed = UPDATE TABLE PERSON SET (PERSON.NAME to "X")

    assertThat(
      listOf(
        renamed.compile(),
        (renamed WHERE (PERSON.ID IS 1L)).compile(),
        (renamed SET (PERSON.AGE to 1)).compile(),
        (renamed WHERE (PERSON.ID IS 2L)).compile()
      ).map(::snapshot)
    ).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """UPDATE "main"."person" SET "name" = ?""",
          arguments = listOf(NativeValue.Text("X"))
        ),
        PlanSnapshot(
          sql = """UPDATE "main"."person" SET "name" = ? WHERE "person"."id" = ?""",
          arguments = listOf(NativeValue.Text("X"), NativeValue.Integer(1))
        ),
        PlanSnapshot(
          sql = """UPDATE "main"."person" SET "name" = ?, "age" = ?""",
          arguments = listOf(NativeValue.Text("X"), NativeValue.Integer(1))
        ),
        PlanSnapshot(
          sql = """UPDATE "main"."person" SET "name" = ? WHERE "person"."id" = ?""",
          arguments = listOf(NativeValue.Text("X"), NativeValue.Integer(2))
        )
      )
    )
  }

  @Test
  fun `every execution binds and closes its own statement`() {
    val statement = mock<SupportSQLiteStatement>()
    val connection = mock<DbConnectionImpl>()
    val sql = """UPDATE "main"."person" SET "name" = ? WHERE "person"."id" = ?"""
    whenever(connection.compileStatement(sql)).thenReturn(statement)
    whenever(statement.executeUpdateDelete()).thenReturn(3)
    val compiled = (UPDATE TABLE PERSON SET (PERSON.NAME to "Ada") WHERE (PERSON.ID IS 1L))
      .usingConnection(connection)
      .compile()

    assertThat(listOf(compiled.execute(), compiled.execute())).isEqualTo(listOf(3, 3))
    inOrder(connection, statement) {
      repeat(2) {
        verify(connection).compileStatement(sql)
        verify(statement).bindString(
          index = 1,
          value = "Ada"
        )
        verify(statement).bindLong(
          index = 2,
          value = 1L
        )
        verify(statement).executeUpdateDelete()
        verify(connection).sendTableTriggers(arrayOf("person"))
        verify(statement).close()
      }
    }
  }

  @Test
  fun `a native failure closes the statement, invalidates and keeps the original exception`() {
    val statement = mock<SupportSQLiteStatement>()
    val connection = mock<DbConnectionImpl>()
    val failure = IllegalStateException("constraint failed")
    val deliveryFailure = IllegalArgumentException("delivery failed")
    whenever(connection.compileStatement("""DELETE FROM "main"."person"""")).thenReturn(statement)
    whenever(statement.executeUpdateDelete()).thenThrow(failure)
    doThrow(deliveryFailure)
      .whenever(connection)
      .sendTableTriggers(arrayOf("person"))

    val actual = assertThrows(IllegalStateException::class.java) {
      (DELETE FROM PERSON)
        .usingConnection(connection)
        .execute()
    }

    assertThat(actual).isSameInstanceAs(failure)
    assertThat(actual.suppressed.toList()).containsExactly(deliveryFailure)
    verify(statement).close()
  }
}
