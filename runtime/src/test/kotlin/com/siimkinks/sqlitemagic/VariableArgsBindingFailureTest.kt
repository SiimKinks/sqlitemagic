package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class VariableArgsBindingFailureTest(
  private val label: String,
  private val update: Boolean,
  private val failBindIndex: Int
) {
  @Test
  fun `binding failure closes the statement before ownership transfers`() =
    assertBindingFailure(closeFailure = null)

  @Test
  fun `binding failure preserves the original error and suppresses cleanup failure`() =
    assertBindingFailure(
      closeFailure = IllegalStateException("close failed")
    )

  @Test
  fun `successfully bound statement remains caller owned`() {
    val fixture = BindingFixture()
    compileStatement(fixture)

    assertThat(fixture.recording.compiledStatements).hasSize(1)
    val recorded = fixture.recording.compiledStatements.single()
    assertThat(recorded.bindings).isEqualTo(
      when {
        update -> mapOf(1 to "value", 2 to "identity")
        else -> mapOf(1 to "value")
      }
    )
    assertThat(recorded.closeCalls).isEqualTo(0)
  }

  private fun assertBindingFailure(closeFailure: RuntimeException?) {
    val bindFailure = AssertionError("binding failed: $label")
    val fixture = BindingFixture(
      bindFailure = bindFailure,
      failBindIndex = failBindIndex,
      closeFailure = closeFailure
    )
    val actual = assertThrows(AssertionError::class.java) {
      compileStatement(fixture)
    }

    assertThat(actual).isSameInstanceAs(bindFailure)
    assertThat(actual.suppressed.toList()).isEqualTo(listOfNotNull(closeFailure))
    assertThat(fixture.recording.compiledStatements).hasSize(1)
    val statement = fixture.recording.compiledStatements.single()
    assertThat(statement.closeCalls).isEqualTo(1)
    assertThat(statement.bindings).isEqualTo(
      when (failBindIndex) {
        2 -> mapOf(1 to "value")
        else -> emptyMap<Int, Any>()
      }
    )
  }

  private fun compileStatement(fixture: BindingFixture): SupportSQLiteStatement {
    val context = OperationContext(
      adapter = TestAdapter(),
      configuration = OperationConfigurationSnapshot(
        connection = fixture.connection,
        conflictAlgorithm = CONFLICT_IGNORE
      )
    )
    context.bindValues["name"] = "value"
    return when {
      update -> context.compileUpdateStatement(
        tableName = "books",
        resolutionColumn = "id",
        resolutionValue = "identity"
      )
      else -> context.compileInsertStatement("books")
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases() = listOf(
      arrayOf<Any>("insert value", false, 1),
      arrayOf<Any>("update value", true, 1),
      arrayOf<Any>("update identity", true, 2)
    )
  }
}

private class BindingFixture(
  bindFailure: Throwable? = null,
  failBindIndex: Int = 1,
  closeFailure: RuntimeException? = null
) {
  val recording = RecordingDatabase()
  private val database = object : SupportSQLiteDatabase by recording {
    override fun compileStatement(sql: String): SupportSQLiteStatement {
      val statement = recording.compileStatement(sql)
      statement.closeFailure = closeFailure
      return object : SupportSQLiteStatement by statement {
        override fun bindString(index: Int, value: String) {
          if (index == failBindIndex) {
            bindFailure?.let { throw it }
          }
          statement.bindString(
            index = index,
            value = value
          )
        }
      }
    }
  }
  val connection = testDbConnection(database)
}
