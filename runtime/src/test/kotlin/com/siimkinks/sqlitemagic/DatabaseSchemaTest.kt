package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException

internal class DatabaseSchemaTest {
  @Test
  fun `open creates the temporary schema`() {
    data class Case(
      val label: String,
      val temporarySchema: List<String>,
      val expected: List<String>
    )

    listOf(
      Case(
        label = "no temporary objects",
        temporarySchema = emptyList(),
        expected = emptyList()
      ),
      Case(
        label = "temporary table",
        temporarySchema = listOf("CREATE TEMP TABLE session (value TEXT)"),
        expected = listOf("session")
      ),
      Case(
        label = "temporary view",
        temporarySchema = listOf("CREATE TEMP VIEW recent AS SELECT 1 AS value"),
        expected = listOf("recent")
      )
    ).forEach { case ->
      val schema = DatabaseSchema(
        resources = { throw FileNotFoundException(it.name) },
        database = TestGeneratedDatabase(
          tableCount = 1,
          temporarySchema = case.temporarySchema
        ),
        downgrader = { _, _, _ -> }
      )
      val actual = BundledSQLiteDriver()
        .open(MEMORY_DATABASE)
        .use { connection ->
          schema.open(connection)
          connection.query("SELECT name FROM sqlite_temp_master") { it.getText(0) }
        }

      assertWithMessage(case.label)
        .that(actual)
        .isEqualTo(case.expected)
    }
  }

  @Test
  fun `upgrade enumerates and closes views before all scripts and recreates once`() {
    val fixture = UpgradeFixture(
      files = mapOf(
        "Feature2.sql" to "SUBMODULE_SQL_V2\n",
        "2.sql" to "MAIN_SQL_V2\n",
        "3.sql" to "MAIN_SQL_V3\n"
      ),
      views = listOf(
        """sub_"quoted"""",
        "retired_view"
      ),
      submoduleNames = listOf("Feature")
    )
    fixture.upgrade(
      oldVersion = 1,
      newVersion = 3
    )
    assertThat(fixture.events)
      .containsExactly(
        "lookup",
        "close",
        """exec:DROP VIEW IF EXISTS main."sub_""quoted"""""",
        """exec:DROP VIEW IF EXISTS main."retired_view"""",
        "open:Feature2.sql",
        "exec:SUBMODULE_SQL_V2",
        "open:2.sql",
        "exec:MAIN_SQL_V2",
        "open:Feature3.sql",
        "open:3.sql",
        "exec:MAIN_SQL_V3",
        "recreate"
      )
      .inOrder()
  }

  @Test
  fun `configure and create use generated database without reading metadata`() {
    val fixture = UpgradeFixture(files = mapOf("sqlitemagic/migrations/manifest.json" to "broken metadata"))
    fixture.applySchemaPragmas()
    fixture.create()
    assertThat(fixture.events)
      .containsExactly("configure:generated", "create:generated")
      .inOrder()
  }

  @Test
  fun `version comes from the generated database and downgrade goes to the downgrader`() {
    val fixture = UpgradeFixture(files = emptyMap())
    fixture.downgrade(
      oldVersion = 7,
      newVersion = 2
    )
    assertThat(fixture.version to fixture.events).isEqualTo(1 to listOf("downgrade:7->2"))
  }

  @Test
  fun `view drop failure stops before SQL and recreation`() {
    val failure = IllegalStateException("cannot drop view")
    val fixture = UpgradeFixture(
      files = mapOf("2.sql" to "MUST_NOT_RUN"),
      views = listOf("retired_view"),
      executionFailures = mapOf("""DROP VIEW IF EXISTS main."retired_view"""" to failure)
    )
    val exception = assertThrows(IllegalStateException::class.java) {
      fixture.upgrade(
        oldVersion = 1,
        newVersion = 2
      )
    }
    assertThat(exception.message).contains("retired_view")
    assertThat(exception.cause).isSameInstanceAs(failure)
    assertThat(fixture.events)
      .containsExactly("lookup", "close", """exec:DROP VIEW IF EXISTS main."retired_view"""")
      .inOrder()
  }
}

private class UpgradeFixture(
  private val files: Map<String, String>,
  private val views: List<String> = emptyList(),
  private val submoduleNames: List<String> = emptyList(),
  private val executionFailures: Map<String, RuntimeException> = emptyMap()
) {
  val events: List<String>
    field = mutableListOf()
  private val connection = mock<SQLiteConnection>()

  init {
    whenever(connection.prepare(any()))
      .thenAnswer { invocation ->
        when (val sql = invocation.getArgument<String>(0)) {
          "SELECT name FROM main.sqlite_master WHERE type = 'view'" -> {
            events += "lookup"
            var position = -1
            mock<SQLiteStatement>().also { statement ->
              whenever(statement.step()).thenAnswer { ++position < views.size }
              whenever(statement.getText(0)).thenAnswer { views[position] }
              doAnswer { events += "close" }
                .whenever(statement)
                .close()
            }
          }
          else -> mock<SQLiteStatement>().also { statement ->
            whenever(statement.step()).thenAnswer {
              events += "exec:$sql"
              executionFailures[sql]?.let { throw it }
              false
            }
          }
        }
      }
  }

  private val schema = DatabaseSchema(
    resources = { resource ->
      events += "open:${resource.name}"
      files[resource.name]?.byteInputStream() ?: throw FileNotFoundException(resource.name)
    },
    database = object : GeneratedDatabase by TestGeneratedDatabase(tableCount = 0) {
      override fun createSchema(connection: SQLiteConnection) {
        events += "create:generated"
      }

      override fun applySchemaPragmas(connection: SQLiteConnection) {
        events += "configure:generated"
      }

      override val submoduleNames
        get() = this@UpgradeFixture.submoduleNames
          .takeIf(List<String>::isNotEmpty)
          ?.toTypedArray()

      override fun migrateViews(connection: SQLiteConnection) {
        events += "recreate"
      }
    },
    downgrader = { _, oldVersion, newVersion -> events += "downgrade:$oldVersion->$newVersion" }
  )

  val version get() = schema.version

  fun applySchemaPragmas() = schema.applySchemaPragmas(connection)

  fun create() = schema.create(connection)

  fun upgrade(
    oldVersion: Int,
    newVersion: Int
  ) = schema.upgrade(
    connection = connection,
    oldVersion = oldVersion,
    newVersion = newVersion
  )

  fun downgrade(
    oldVersion: Int,
    newVersion: Int
  ) = schema.downgrade(
    connection = connection,
    oldVersion = oldVersion,
    newVersion = newVersion
  )
}
