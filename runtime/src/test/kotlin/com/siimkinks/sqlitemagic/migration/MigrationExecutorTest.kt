package com.siimkinks.sqlitemagic.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class MigrationExecutorTest {
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
    val executed = mutableListOf<String>()
    val failure = IllegalArgumentException("SQLite failure")
    val sql = "INSERT INTO sample VALUES ('õ;''quoted');"
    val database = object : MigrationDatabase {
      override fun execute(sql: String) {
        executed += sql
        if (sql == "BAD_SQL") throw failure
      }

      override fun persistentViewNames() = emptyList<String>()
    }
    val exception = assertThrows(MigrationException::class.java) {
      MigrationExecutor(
        resources = { "\n -- ignored\n$sql\n\nBAD_SQL\n".byteInputStream() },
        database = database
      ).execute(
        plan = plan(),
        createTargetViews = { error("Must not recreate views after failure") }
      )
    }
    assertThat(executed)
      .containsExactly(sql, "BAD_SQL")
      .inOrder()
    assertThat(exception.message).contains("line 5 (statement 2, migration 1 -> 2, step 2)")
    assertThat(exception.cause).isSameInstanceAs(failure)
    assertThat(exception.stepVersion).isEqualTo(2)
    assertThat(exception.statementText).isEqualTo("BAD_SQL")
  }

  @Test
  fun `SQL lexical boundaries preserve quoted identifiers and inline comments`() {
    val statements = listOf(
      "SELECT 'literal;value'; -- trailing comment",
      "SELECT \"quoted;\"\"identifier\";",
      "SELECT `backtick;identifier`;",
      "SELECT [bracket;identifier];",
      "SELECT (1 + 2) /* same line comment */;"
    )
    val database = RecordingDatabase()
    MigrationExecutor(
      resources = {
        statements
          .joinToString("\n")
          .byteInputStream()
      },
      database = database
    ).execute(
      plan = plan(),
      createTargetViews = {}
    )
    assertThat(database.statements)
      .containsExactlyElementsIn(statements)
      .inOrder()
  }

  @Test
  fun `resource open runtime failures preserve context and VM errors propagate`() {
    val failure = IllegalArgumentException("resource unavailable")
    val exception = assertThrows(MigrationException::class.java) {
      MigrationExecutor(
        resources = { throw failure },
        database = RecordingDatabase()
      ).execute(
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
        database = RecordingDatabase()
      ).execute(
        plan = plan(),
        createTargetViews = {}
      )
    }
    assertThat(thrown).isSameInstanceAs(error)
  }

  @Test
  fun `all persistent views drop once before SQL without reading view resources`() {
    val events = mutableListOf<String>()
    val database = object : MigrationDatabase {
      override fun execute(sql: String) {
        events += sql
      }

      override fun persistentViewNames(): List<String> {
        events += "lookup"
        return listOf("quoted\"view", "--literal_view")
      }
    }
    val files = mapOf(
      "2.views" to "quoted\"view\ntable\n",
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
      database = database
    ).execute(
      plan = MigrationPlanner.plan(
        fromVersion = 1,
        toVersion = 3
      ),
      createTargetViews = { events += "recreate" }
    )
    assertThat(events)
      .containsExactly(
        "lookup",
        "DROP VIEW IF EXISTS main.\"quoted\"\"view\"",
        "DROP VIEW IF EXISTS main.\"--literal_view\"",
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
    val cases = listOf(
      Triple(
        first = "required missing",
        second = MigrationResource("2.sql"),
        third = FileNotFoundException("missing")
      ),
      Triple(
        first = "required unreadable",
        second = MigrationResource("2.sql"),
        third = IOException("unreadable")
      ),
      Triple(
        first = "optional unreadable",
        second = optional("2.sql"),
        third = IOException("unreadable")
      )
    )
    cases.forEach { (label, resource, cause) ->
      val exception = assertThrows(label, MigrationException::class.java) {
        MigrationExecutor(
          resources = { throw cause },
          database = RecordingDatabase()
        ).execute(
          plan = plan(resource),
          createTargetViews = { error(label) }
        )
      }
      assertThat(exception.cause).isSameInstanceAs(cause)
    }
    var recreated = false
    MigrationExecutor(
      resources = { throw FileNotFoundException() },
      database = RecordingDatabase()
    ).execute(
      plan = plan(optional("2.sql")),
      createTargetViews = { recreated = true }
    )
    assertThat(recreated).isTrue()
  }

  @Test
  fun `required empty scripts fail before recreation`() {
    val database = RecordingDatabase()
    val failure = assertThrows(MigrationException::class.java) {
      MigrationExecutor(
        resources = { "-- empty\n\n".byteInputStream() },
        database = database
      ).execute(
        plan = plan(),
        createTargetViews = { error("Empty required script must stop recreation") }
      )
    }
    assertThat(database.statements).isEmpty()
    assertThat(failure.cause).hasMessageThat().contains("Migration script contains no executable statements")
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
          database = RecordingDatabase()
        ).execute(
          plan = plan(),
          createTargetViews = { error(label) }
        )
      }
      assertThat(exception.cause).isInstanceOf(IOException::class.java)
      assertThat(exception.resourceName).isEqualTo("2.sql")
    }
  }

  @Test
  fun `equal versions produce empty plans including the maximum integer version`() {
    val expected = MigrationPlan(
      fromVersion = 2,
      toVersion = 2,
      steps = emptyList()
    )
    val actual = MigrationPlanner.plan(
      fromVersion = 2,
      toVersion = 2
    )
    assertThat(actual).isEqualTo(expected)
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

private class RecordingDatabase : MigrationDatabase {
  val statements = mutableListOf<String>()
  override fun execute(sql: String) {
    statements += sql
  }

  override fun persistentViewNames() = emptyList<String>()
}
