package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.internal.GeneratedSchema
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.SqlUtil
import org.junit.Assert.assertThrows
import org.junit.Test

internal class SchemaCreationTest {
  private class PersonSchema(
    private val indexFailure: RuntimeException? = null
  ) : GeneratedSchema {
    override fun createSchemaTables(db: SupportSQLiteDatabase, temporary: Boolean) = db.execSQL(
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

    override fun createSchemaIndexes(db: SupportSQLiteDatabase, temporary: Boolean) {
      indexFailure?.let { throw it }
      if (!temporary) {
        db.execSQL(PERSON_INDEX)
      }
    }

    override fun clearTables(db: SupportSQLiteDatabase, changedTables: MutableScatterSet<String>) {
      db.execSQL("DELETE FROM person")
      changedTables.add("person")
    }
  }

  private class RecordingSchemaDatabase(
    private val database: BundledSqliteDatabase
  ) : SupportSQLiteDatabase by database {
    val events: List<String>
      field = mutableListOf()

    override fun execSQL(sql: String) {
      events += sql
      database.execSQL(sql)
    }

    override fun beginTransaction() {
      events += "BEGIN"
      database.beginTransaction()
    }

    override fun setTransactionSuccessful() {
      events += "SUCCESS"
      database.setTransactionSuccessful()
    }

    override fun endTransaction() {
      events += "END"
      database.endTransaction()
    }
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
        expectedEvents = listOf("BEGIN", PERSON_TABLE, PERSON_NAMES_VIEW, PERSON_INDEX, "SUCCESS", "END")
      ),
      Case(
        label = "temporary",
        temporary = true,
        expectedEvents = listOf("BEGIN", SESSION_TABLE, "SUCCESS", "END")
      )
    ).forEach { case ->
      BundledSqliteDatabase().use { database ->
        val recording = RecordingSchemaDatabase(database)
        SqlUtil.createSchema(
          db = recording,
          schema = PersonSchema(),
          temporary = case.temporary
        )

        assertWithMessage(case.label)
          .that(recording.events)
          .isEqualTo(case.expectedEvents)
      }
    }
  }

  @Test
  fun `a failure rolls back earlier schema work and propagates unchanged`() = BundledSqliteDatabase().use { database ->
    val recording = RecordingSchemaDatabase(database)
    val failure = IllegalStateException("index failure")

    val actual = assertThrows(IllegalStateException::class.java) {
      SqlUtil.createSchema(
        db = recording,
        schema = PersonSchema(indexFailure = failure),
        temporary = false
      )
    }

    assertThat(actual).isSameInstanceAs(failure)
    assertThat(recording.events).isEqualTo(listOf("BEGIN", PERSON_TABLE, PERSON_NAMES_VIEW, "END"))
    assertThat(
      SELECT
        .RAW("SELECT count(*) FROM sqlite_master")
        .readAs(LONG)
        .usingConnection(database.dbConnection())
        .execute()
    ).isEqualTo(listOf(0L))
  }

  @Test
  fun `view migration creates persistent views outside a transaction`() = BundledSqliteDatabase().use { database ->
    database.execSQL(PERSON_TABLE)
    val recording = RecordingSchemaDatabase(database)

    SqlUtil.migrateViews(
      db = recording,
      schema = PersonSchema()
    )

    assertThat(recording.events).isEqualTo(listOf(PERSON_NAMES_VIEW))
  }

  @Test
  fun `clearing data deletes rows in one transaction and reports the changed tables`() =
    BundledSqliteDatabase().use { database ->
      database.execSQL(PERSON_TABLE)
      val recording = RecordingSchemaDatabase(database)

      val changedTables = SqlUtil.clearData(
        db = recording,
        schema = PersonSchema(),
        tableCount = 1
      )

      assertThat(changedTables.asSet()).containsExactly("person")
      assertThat(recording.events).isEqualTo(listOf("BEGIN", "DELETE FROM person", "SUCCESS", "END"))
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
