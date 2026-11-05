package com.siimkinks.sqlitemagic.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.NoRowStatement
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

internal class MigrationExecutorTest {
  @Test
  fun `conventional planning preserves version and module ordering with optional empty resources`() {
    val actual = MigrationPlanner.plan(
      fromVersion = 1,
      toVersion = 2,
      submoduleNames = listOf("Feature", "Accounts")
    )
    val expected = MigrationPlan(
      fromVersion = 1,
      toVersion = 2,
      steps = listOf(
        MigrationStep(
          toVersion = 2,
          sqlResources = listOf("Feature2.sql", "Accounts2.sql", "2.sql")
            .map(::optional)
        )
      )
    )
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `SQL reader preserves UTF8 literals semicolons and physical statement diagnostics`() {
    val failure = IllegalArgumentException("SQLite failure")
    val sql = "INSERT INTO sample VALUES ('õ;''quoted');"
    val connection = RecordingMigrationConnection(failures = mapOf("BAD_SQL" to failure))
    val exception = assertThrows(MigrationException::class.java) {
      MigrationExecutor(
        resources = { "\n -- ignored\n$sql\n\nBAD_SQL\n".byteInputStream() },
        connection = connection
      )
        .execute(
          plan = plan(),
          createTargetViews = { error("Must not recreate views after failure") }
        )
    }
    assertThat(connection.events)
      .containsExactly("lookup", sql, "BAD_SQL")
      .inOrder()
    assertThat(exception.message)
      .isEqualTo("Error executing migration script 2.sql at line 5 (statement 2, migration 1 -> 2, step 2): BAD_SQL")
    assertThat(exception.cause).isSameInstanceAs(failure)
    assertThat(exception.stepVersion).isEqualTo(2)
    assertThat(exception.statementText).isEqualTo("BAD_SQL")
  }

  @Test
  fun `SQL lexical boundaries preserve quoted identifiers and inline comments`() {
    val statements = listOf(
      "SELECT 'literal;value'; -- trailing comment",
      """SELECT "quoted;""identifier";""",
      "SELECT `backtick;identifier`;",
      "SELECT [bracket;identifier];",
      "SELECT (1 + 2) /* same line comment */;"
    )
    val connection = RecordingMigrationConnection()
    MigrationExecutor(
      resources = {
        statements
          .joinToString("\n")
          .byteInputStream()
      },
      connection = connection
    )
      .execute(
        plan = plan(),
        createTargetViews = {}
      )
    assertThat(connection.events)
      .containsExactlyElementsIn(listOf("lookup") + statements)
      .inOrder()
  }

  @Test
  fun `lines ending the upgrade's transaction fail the script before reaching the connection`() {
    data class Outcome(
      val events: List<String>,
      val physicalLine: Int,
      val cause: Class<*>?
    )

    val created = "CREATE TABLE added (value TEXT);"
    listOf(
      "COMMIT;",
      "end transaction;",
      "ROLLBACK;",
      "  /* keeps the upgrade */ COMMIT;",
      "\uFEFFrollback transaction;",
      "ROLLBACK /* TO step */;"
    ).forEach { line ->
      val connection = RecordingMigrationConnection()
      val exception = assertThrows(line, MigrationException::class.java) {
        MigrationExecutor(
          resources = { "$created\n$line\n".byteInputStream() },
          connection = connection
        )
          .execute(
            plan = plan(),
            createTargetViews = { error("Must not recreate views after failure") }
          )
      }
      assertWithMessage(line)
        .that(
          Outcome(
            events = connection.events,
            physicalLine = exception.physicalLine,
            cause = exception.cause?.javaClass
          )
        )
        .isEqualTo(
          Outcome(
            events = listOf("lookup", created),
            physicalLine = 2,
            cause = IllegalArgumentException::class.java
          )
        )
    }
  }

  @Test
  fun `savepoint lines and other statements named like transaction control reach the connection`() {
    val statements = listOf(
      "BEGIN;",
      "SAVEPOINT step;",
      "ROLLBACK TO step;",
      "rollback transaction /* step */ to savepoint step;",
      "RELEASE step;",
      "UPDATE sample SET value = 'COMMIT';",
      "COMMITMENT;",
      "ENDõ;"
    )
    val connection = RecordingMigrationConnection()
    MigrationExecutor(
      resources = {
        statements
          .joinToString("\n")
          .byteInputStream()
      },
      connection = connection
    )
      .execute(
        plan = plan(),
        createTargetViews = {}
      )
    assertThat(connection.events)
      .containsExactlyElementsIn(listOf("lookup") + statements)
      .inOrder()
  }

  @Test
  fun `resource open runtime failures preserve context and VM errors propagate`() {
    val failure = IllegalArgumentException("resource unavailable")
    val exception = assertThrows(MigrationException::class.java) {
      MigrationExecutor(
        resources = { throw failure },
        connection = RecordingMigrationConnection()
      )
        .execute(
          plan = plan(),
          createTargetViews = {}
        )
    }
    assertThat(exception.cause).isSameInstanceAs(failure)
    assertThat(exception.statementText).isNull()
    val error = LinkageError("native engine unavailable")
    val thrown = assertThrows(LinkageError::class.java) {
      MigrationExecutor(
        resources = { throw error },
        connection = RecordingMigrationConnection()
      )
        .execute(
          plan = plan(),
          createTargetViews = {}
        )
    }
    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `all persistent views drop once before SQL without reading view resources`() {
    val events = mutableListOf<String>()
    val connection = RecordingMigrationConnection(
      viewNames = listOf("""quoted"view""", "--literal_view"),
      events = events
    )
    val files = mapOf(
      "2.views" to """quoted"view""" + "\ntable\n",
      "3.views" to "--literal_view\n",
      "2.sql" to "SELECT 2;\n",
      "3.sql" to "SELECT 3\n"
    )
    MigrationExecutor(
      resources = { resource ->
        events += "open:${resource.name}"
        files.getValue(resource.name)
          .byteInputStream()
      },
      connection = connection
    )
      .execute(
        plan = MigrationPlanner.plan(
          fromVersion = 1,
          toVersion = 3
        ),
        createTargetViews = { events += "recreate" }
      )
    assertThat(events)
      .containsExactly(
        "lookup",
        """DROP VIEW IF EXISTS main."quoted""view"""",
        """DROP VIEW IF EXISTS main."--literal_view"""",
        "open:2.sql",
        "SELECT 2;",
        "open:3.sql",
        "SELECT 3",
        "recreate"
      )
      .inOrder()
  }

  @Test
  fun `only missing optional resources are skipped`() {
    data class ResourceCase(
      val label: String,
      val resource: MigrationResource,
      val cause: IOException
    )
    listOf(
      ResourceCase(
        label = "required missing",
        resource = MigrationResource("2.sql"),
        cause = FileNotFoundException("missing")
      ),
      ResourceCase(
        label = "optional unreadable",
        resource = optional("2.sql"),
        cause = IOException("unreadable")
      )
    ).forEach { case ->
      val exception = assertThrows(case.label, MigrationException::class.java) {
        MigrationExecutor(
          resources = { throw case.cause },
          connection = RecordingMigrationConnection()
        )
          .execute(
            plan = plan(case.resource),
            createTargetViews = { error(case.label) }
          )
      }
      assertThat(exception.cause).isSameInstanceAs(case.cause)
    }
    var recreated = false
    MigrationExecutor(
      resources = { throw FileNotFoundException() },
      connection = RecordingMigrationConnection()
    )
      .execute(
        plan = plan(optional("2.sql")),
        createTargetViews = { recreated = true }
      )
    assertThat(recreated).isTrue()
  }

  @Test
  fun `required empty scripts fail before recreation`() {
    val connection = RecordingMigrationConnection()
    val failure = assertThrows(MigrationException::class.java) {
      MigrationExecutor(
        resources = { "-- empty\n\n".byteInputStream() },
        connection = connection
      )
        .execute(
          plan = plan(),
          createTargetViews = { error("Empty required script must stop recreation") }
        )
    }
    assertThat(connection.events).containsExactly("lookup")
    assertThat(failure.cause)
      .hasMessageThat()
      .contains("Migration script contains no executable statements")
  }

  @Test
  fun `malformed UTF8 read and close failures retain their cause`() {
    val closeFailure = IOException("close failure")
    val streams = listOf(
      "malformed UTF8" to ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)),
      "read failure" to object : InputStream() {
        override fun read(): Int = throw IOException("read failure")
      },
      "close failure" to object : ByteArrayInputStream("SELECT 1".toByteArray()) {
        override fun close(): Unit = throw closeFailure
      }
    )
    streams.forEach { (label, stream) ->
      val exception = assertThrows(label, MigrationException::class.java) {
        MigrationExecutor(
          resources = { stream },
          connection = RecordingMigrationConnection()
        )
          .execute(
            plan = plan(),
            createTargetViews = { error(label) }
          )
      }
      assertThat(exception.cause).isInstanceOf(IOException::class.java)
      assertThat(exception.resourceName).isEqualTo("2.sql")
    }
  }

  @Test
  fun `equal maximum integer versions produce an empty plan`() {
    val maximumVersionPlan = MigrationPlanner.plan(
      fromVersion = Int.MAX_VALUE,
      toVersion = Int.MAX_VALUE
    )
    val expectedMaximumVersionPlan = MigrationPlan(
      fromVersion = Int.MAX_VALUE,
      toVersion = Int.MAX_VALUE,
      steps = emptyList()
    )
    assertThat(maximumVersionPlan).isEqualTo(expectedMaximumVersionPlan)
  }
}

private fun optional(name: String) = MigrationResource(
  name = name,
  required = false,
  allowEmpty = true
)

private fun plan(resource: MigrationResource = MigrationResource("2.sql")) = MigrationPlan(
  fromVersion = 1,
  toVersion = 2,
  steps = listOf(
    MigrationStep(
      toVersion = 2,
      sqlResources = listOf(resource)
    )
  )
)

private class RecordingMigrationConnection(
  private val viewNames: List<String> = emptyList(),
  private val failures: Map<String, RuntimeException> = emptyMap(),
  val events: MutableList<String> = mutableListOf()
) : SQLiteConnection {
  override fun prepare(sql: String): SQLiteStatement = when {
    sql.startsWith("SELECT name FROM main.sqlite_master") -> {
      events += "lookup"
      ViewNamesStatement(viewNames.iterator())
    }
    else -> ExecutedStatement(sql)
  }

  override fun inTransaction() = false

  override fun close() = Unit

  private inner class ExecutedStatement(private val sql: String) : NoRowStatement() {
    override fun step(): Boolean {
      events += sql
      failures[sql]?.let { throw it }
      return false
    }
  }

  private class ViewNamesStatement(private val names: Iterator<String>) : NoRowStatement() {
    private var name = ""

    override fun step(): Boolean {
      if (!names.hasNext()) {
        return false
      }
      name = names.next()
      return true
    }

    override fun getText(index: Int) = name
  }
}
