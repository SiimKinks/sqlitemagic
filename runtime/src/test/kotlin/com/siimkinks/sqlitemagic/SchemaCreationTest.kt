package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import com.siimkinks.sqlitemagic.connection.TrackingDriver
import com.siimkinks.sqlitemagic.connection.long
import com.siimkinks.sqlitemagic.internal.GeneratedSchema
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.SqlUtil.clearData
import com.siimkinks.sqlitemagic.internal.SqlUtil.createSchema
import com.siimkinks.sqlitemagic.internal.SqlUtil.migrateViews
import org.junit.Assert.assertThrows
import org.junit.Test

internal class SchemaCreationTest {
  private class PersonSchema(
    private val indexFailure: RuntimeException? = null
  ) : GeneratedSchema {
    override fun createSchemaTables(connection: SQLiteConnection, temporary: Boolean) = connection.execSQL(
      when {
        temporary -> SESSION_TABLE
        else -> PERSON_TABLE
      }
    )

    override fun collectGeneratedViews(views: MutableList<GeneratedView>) {
      views += GeneratedView(
        viewName = "person_names",
        temporary = false,
        query = {
          SELECT
            .COLUMN(PERSON.NAME)
            .FROM(PERSON)
        },
        columns = listOf("person.name")
      )
    }

    override fun createSchemaIndexes(connection: SQLiteConnection, temporary: Boolean) {
      if (indexFailure != null) {
        throw indexFailure
      }
      if (!temporary) {
        connection.execSQL(PERSON_INDEX)
      }
    }

    override fun clearTables(connection: SQLiteConnection, changedTables: MutableScatterSet<String>) {
      connection.execSQL("DELETE FROM person")
      changedTables.add("person")
    }

    override fun collectCascadeTables(cascadeTables: MutableScatterMap<String, Array<String>>) = Unit
  }

  @Test
  fun `each schema creates its tables then views then indexes in one transaction`() {
    data class Case(
      val label: String,
      val temporary: Boolean,
      val expectedEvents: List<String>
    )

    listOf(
      Case(
        label = "persistent",
        temporary = false,
        expectedEvents = listOf("BEGIN IMMEDIATE", PERSON_TABLE, PERSON_NAMES_VIEW, PERSON_INDEX, "COMMIT")
      ),
      Case(
        label = "temporary",
        temporary = true,
        expectedEvents = listOf("BEGIN IMMEDIATE", SESSION_TABLE, "COMMIT")
      )
    ).forEach { case ->
      TrackingDriver()
        .open(MEMORY_DATABASE)
        .use { connection ->
          createSchema(
            connection = connection,
            schema = PersonSchema(),
            temporary = case.temporary
          )

          assertWithMessage(case.label)
            .that(connection.prepared)
            .isEqualTo(case.expectedEvents)
        }
    }
  }

  @Test
  fun `a failure rolls back earlier schema work and propagates unchanged`() = TrackingDriver()
    .open(MEMORY_DATABASE)
    .use { connection ->
      val failure = IllegalStateException("index failure")

      val actual = assertThrows(IllegalStateException::class.java) {
        createSchema(
          connection = connection,
          schema = PersonSchema(indexFailure = failure),
          temporary = false
        )
      }

      assertThat(actual).isSameInstanceAs(failure)
      assertThat(connection.prepared).isEqualTo(listOf("BEGIN IMMEDIATE", PERSON_TABLE, PERSON_NAMES_VIEW, "ROLLBACK"))
      assertThat(connection.long("SELECT count(*) FROM sqlite_master")).isEqualTo(0L)
    }

  @Test
  fun `view migration creates persistent views outside a transaction`() = TrackingDriver()
    .open(MEMORY_DATABASE)
    .use { connection ->
      connection.execSQL(PERSON_TABLE)
      connection.prepared.clear()

      migrateViews(
        connection = connection,
        schema = PersonSchema()
      )

      assertThat(connection.prepared).isEqualTo(listOf(PERSON_NAMES_VIEW))
    }

  @Test
  fun `clearing data deletes rows in one transaction and reports the changed tables`() = TrackingDriver()
    .open(MEMORY_DATABASE)
    .use { connection ->
      connection.execSQL(PERSON_TABLE)
      connection.prepared.clear()

      val changedTables = clearData(
        connection = connection,
        schema = PersonSchema(),
        tableCount = 1
      )

      assertThat(changedTables.asSet()).containsExactly("person")
      assertThat(connection.prepared).isEqualTo(listOf("BEGIN IMMEDIATE", "DELETE FROM person", "COMMIT"))
    }

  private companion object {
    const val PERSON_TABLE = "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT, age INTEGER NOT NULL, " +
        "parent_id INTEGER, token TEXT NOT NULL, flag INTEGER)"
    const val PERSON_NAMES_VIEW = """CREATE VIEW IF NOT EXISTS "person_names" AS """ +
        """SELECT "person"."name" AS "person.name" FROM "main"."person""""
    const val PERSON_INDEX = "CREATE INDEX person_name_index ON person (name)"
    const val SESSION_TABLE = "CREATE TEMPORARY TABLE session (value TEXT)"
  }
}
