package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.MutationResults
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class VariableArgsBindingFailureTest(
  private val label: String,
  private val update: Boolean,
  private val failBindIndex: Int
) {
  private val expectedBindings = when {
    update -> mapOf(1 to "value", 2 to "identity")
    else -> mapOf(1 to "value")
  }

  @Test
  fun `binding failure releases the statement for the next execution`() {
    val bindFailure = AssertionError("binding failed: $label")
    val fixture = BindingFixture(
      bindFailure = bindFailure,
      failBindIndex = failBindIndex
    )

    val actual = assertThrows(AssertionError::class.java) { execute(fixture) }
    execute(fixture)

    assertThat(actual).isSameInstanceAs(bindFailure)
    assertThat(actual.suppressed).isEmpty()
    val statement = fixture.recording.compiledStatements.single()
    assertThat(fixture.recording.executions).containsExactly(
      RecordedExecution(
        sql = statement.sql,
        bindings = expectedBindings
      )
    )
    assertThat(statement.closeCalls).isEqualTo(0)
  }

  @Test
  fun `binding failure keeps the original error when the statement cannot be reset`() {
    val bindFailure = AssertionError("binding failed: $label")
    val fixture = BindingFixture(
      bindFailure = bindFailure,
      failBindIndex = failBindIndex,
      resetFailure = IllegalStateException("reset failed")
    )

    val actual = assertThrows(AssertionError::class.java) { execute(fixture) }
    execute(fixture)

    assertThat(actual).isSameInstanceAs(bindFailure)
    assertThat(actual.suppressed).isEmpty()
    assertThat(fixture.recording.compiledStatements.map(RecordingStatement::closeCalls)).isEqualTo(listOf(1, 0))
    assertThat(fixture.recording.executions.map(RecordedExecution::bindings)).containsExactly(expectedBindings)
  }

  @Test
  fun `successfully bound statement executes with its bindings`() {
    val fixture = BindingFixture()
    execute(fixture)

    val statement = fixture.recording.compiledStatements.single()
    assertThat(fixture.recording.executions).containsExactly(
      RecordedExecution(
        sql = statement.sql,
        bindings = expectedBindings
      )
    )
    assertThat(statement.closeCalls).isEqualTo(0)
  }

  private fun execute(fixture: BindingFixture): Any {
    val context = OperationContext(
      adapter = TestAdapter(),
      configuration = OperationConfigurationSnapshot(
        connection = fixture.connection,
        conflictAlgorithm = CONFLICT_IGNORE
      )
    )
    context.bindValues["name"] = "value"
    return when {
      update -> context.executeBoundUpdate(
        tableName = "books",
        resolutionColumn = "id",
        resolutionValue = "identity"
      )
      else -> context.executeBoundInsert("books")
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun cases() = listOf(
      arrayOf<Any>("insert value", false, 1),
      arrayOf<Any>("update value", true, 1),
      arrayOf<Any>("update identity", true, 2)
    )
  }
}

private class BindingFixture(
  private var bindFailure: Throwable? = null,
  private val failBindIndex: Int = 1,
  private var resetFailure: RuntimeException? = null
) {
  val recording = RecordingSQLiteConnection()
  val connection = testDbConnection(
    driver = SingleConnectionDriver(
      object : SQLiteConnection by recording, MutationResults by recording {
        override fun prepare(sql: String) = when (val statement = recording.prepare(sql)) {
          is RecordingStatement -> object : SQLiteStatement by statement {
            override fun bindText(index: Int, value: String) {
              if (index == failBindIndex) {
                bindFailure?.let {
                  bindFailure = null
                  throw it
                }
              }
              statement.bindText(
                index = index,
                value = value
              )
            }

            override fun reset() {
              resetFailure?.let {
                resetFailure = null
                throw it
              }
              statement.reset()
            }
          }
          else -> statement
        }
      }
    )
  )
}
