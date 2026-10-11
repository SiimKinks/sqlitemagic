package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import io.reactivex.schedulers.Schedulers
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
    val statement = compileStatement(fixture)

    assertThat(fixture.recording.compiledStatements).hasSize(1)
    val recorded = fixture.recording.compiledStatements.single()
    assertThat(recorded.bindings).isEqualTo(
      when {
        update -> mapOf(1 to "value", 2 to "identity")
        else -> mapOf(1 to "value")
      }
    )
    assertThat(recorded.closeCalls).isEqualTo(0)
    assertThat(fixture.nativeExecutions).isEqualTo(0)
    assertThat(fixture.triggers).isEmpty()

    when {
      update -> assertThat(statement.executeUpdateDelete()).isEqualTo(1)
      else -> assertThat(statement.executeInsert()).isEqualTo(1L)
    }
    statement.close()
    assertThat(fixture.nativeExecutions).isEqualTo(1)
    assertThat(recorded.closeCalls).isEqualTo(1)
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
    assertThat(fixture.nativeExecutions).isEqualTo(0)
    assertThat(fixture.triggers).isEmpty()
  }

  private fun compileStatement(fixture: BindingFixture): SupportSQLiteStatement {
    val values = MutableScatterMap<String, Any>().apply { put("name", "value") }
    val helper = VariableArgsOperationHelper(conflictAlgorithm = CONFLICT_IGNORE)
    val manager = EntityDbManager(fixture.connection)
    return when {
      update -> helper.compileUpdateStatement(
        tableName = "books",
        maxColumns = 1,
        values = values,
        resolutionColumn = "id",
        resolutionValue = "identity",
        manager = manager
      )
      else -> helper.compileInsertStatement(
        tableName = "books",
        maxColumns = 1,
        values = values,
        manager = manager
      )
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
  var nativeExecutions = 0
  private val database = object : SupportSQLiteDatabase by recording {
    override fun compileStatement(sql: String): SupportSQLiteStatement {
      val statement = recording.compileStatement(sql)
      statement.closeFailure = closeFailure
      return object : SupportSQLiteStatement by statement {
        override fun bindString(index: Int, value: String) {
          if (index == failBindIndex) {
            bindFailure?.let { throw it }
          }
          statement.bindString(index, value)
        }

        override fun execute() {
          nativeExecutions++
          statement.execute()
        }

        override fun executeInsert(): Long {
          nativeExecutions++
          return statement.executeInsert()
        }

        override fun executeUpdateDelete(): Int {
          nativeExecutions++
          return statement.executeUpdateDelete()
        }
      }
    }
  }
  private val helper = object : SupportSQLiteOpenHelper by RecordingOpenHelper(recording) {
    override val writableDatabase = database
    override val readableDatabase = database
  }
  val connection = DbConnectionImpl(
    database = TestGeneratedDatabase(tableCount = 1),
    dbHelper = helper,
    queryScheduler = Schedulers.trampoline()
  )
  val triggers = mutableListOf<Set<String>>()

  init {
    connection.triggers
      .map(Set<String>::toSet)
      .subscribe(triggers::add)
  }
}
