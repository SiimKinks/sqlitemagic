package com.siimkinks.sqlitemagic.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MigrationScriptValidationTest {
  @Test
  fun `explicit transaction controls are rejected before reaching the database`() {
    val commands = listOf(
      "BEGIN", "COMMIT", "END", "ROLLBACK", "SAVEPOINT migration", "RELEASE migration", "ROLLBACK TO migration"
    )
    val prefixes = listOf("", "  /* leading COMMIT text */ ", "\uFEFF")
    commands.forEach { command ->
      prefixes.forEach { prefix ->
        val statement = prefix + command.lowercase() + ";"
        val failure = assertThrows(statement, IllegalArgumentException::class.java) { validateSqlLine(statement) }
        assertThat(failure.message).contains("Unsupported migration transaction control")
      }
    }
    val accepted = listOf(
      "UPDATE sample SET value='COMMIT'",
      "/* COMMIT */ UPDATE sample SET \"BEGIN\"='RELEASE'",
      "SELECT 'ROLLBACK', \"SAVEPOINT\", `END`, [BEGIN]",
      "COMMITMENT", "RELEASE_NOTE", "BEGIN2", "COMMIT\$suffix", "COMMITõ", "COMMıT"
    )
    accepted.forEach(::validateSqlLine)
  }

  @Test
  fun `authored scripts require statements unless empty input is explicitly allowed`() {
    listOf("", "\n  -- explanation\n ").forEach { text ->
      MigrationScriptValidation.validate(
        text = text,
        resourceName = "3.sql",
        allowEmpty = true
      )
      val failure = assertThrows(IllegalStateException::class.java) {
        MigrationScriptValidation.validate(
          text = text,
          resourceName = "3.sql",
          allowEmpty = false
        )
      }
      assertThat(failure.message).contains("Empty migration script 3.sql")
    }
    MigrationScriptValidation.validate(
      text = "-- explanation\nUPDATE sample SET value=1\n",
      resourceName = "3.sql",
      allowEmpty = false
    )
  }
}
