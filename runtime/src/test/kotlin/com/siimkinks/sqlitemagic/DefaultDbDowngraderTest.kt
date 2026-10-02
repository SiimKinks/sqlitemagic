package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.FailureStage.CLOSE
import com.siimkinks.sqlitemagic.FailureStage.CREATE
import com.siimkinks.sqlitemagic.FailureStage.DROP
import com.siimkinks.sqlitemagic.FailureStage.MOVE
import com.siimkinks.sqlitemagic.FailureStage.READ
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class DefaultDbDowngraderTest {
  @Test
  fun `downgrade drops every returned table in order before closing and recreating the schema`() {
    val fixture = DowngradeFixture(tableNames = listOf("first_table", "second_table"))

    fixture.downgrade()

    assertThat(fixture.events)
      .containsExactly(
        "query",
        "drop:DROP TABLE IF EXISTS first_table",
        "drop:DROP TABLE IF EXISTS second_table",
        "close",
        "create"
      )
      .inOrder()
  }

  @Test
  fun `downgrade with no tables closes the cursor before recreating the schema`() {
    val fixture = DowngradeFixture(tableNames = emptyList())

    fixture.downgrade()

    assertThat(fixture.events)
      .containsExactly("query", "close", "create")
      .inOrder()
  }

  @Test
  fun `cursor and drop failures close the cursor and prevent schema recreation`() {
    listOf(
      setOf(MOVE),
      setOf(READ),
      setOf(DROP),
      setOf(MOVE, CLOSE),
      setOf(DROP, CLOSE)
    ).forEach { stages ->
      val fixture = DowngradeFixture(
        tableNames = listOf("first_table", "second_table"),
        failureStages = stages
      )

      val failure = assertThrows(
        IllegalStateException::class.java,
        fixture::downgrade
      )

      assertWithMessage(stages.joinToString())
        .that(failure)
        .isSameInstanceAs(fixture.failure)
      assertWithMessage(stages.joinToString())
        .that(failure.suppressed.toList())
        .containsExactlyElementsIn(
          when {
            CLOSE in stages -> listOf(fixture.closeFailure)
            else -> emptyList()
          }
        )
      assertWithMessage(stages.joinToString())
        .that(fixture.events)
        .containsExactlyElementsIn(
          when {
            DROP in stages -> listOf("query", "drop:DROP TABLE IF EXISTS first_table", "close")
            else -> listOf("query", "close")
          }
        )
        .inOrder()
    }
  }

  @Test
  fun `cursor close failure propagates and prevents schema recreation`() {
    val fixture = DowngradeFixture(
      tableNames = emptyList(),
      failureStages = setOf(CLOSE)
    )

    val failure = assertThrows(
      IllegalStateException::class.java,
      fixture::downgrade
    )

    assertThat(failure)
      .isSameInstanceAs(fixture.closeFailure)
    assertThat(failure.suppressed)
      .isEmpty()
    assertThat(fixture.events)
      .containsExactly("query", "close")
      .inOrder()
  }

  @Test
  fun `schema recreation failure propagates after closing the cursor`() {
    val fixture = DowngradeFixture(
      tableNames = emptyList(),
      failureStages = setOf(CREATE)
    )

    val failure = assertThrows(
      IllegalStateException::class.java,
      fixture::downgrade
    )

    assertThat(failure)
      .isSameInstanceAs(fixture.failure)
    assertThat(failure.suppressed)
      .isEmpty()
    assertThat(fixture.events)
      .containsExactly("query", "close", "create")
      .inOrder()
  }
}

private enum class FailureStage {
  MOVE,
  READ,
  DROP,
  CLOSE,
  CREATE
}

private class DowngradeFixture(
  tableNames: List<String>,
  private val failureStages: Set<FailureStage> = emptySet()
) {
  val events: List<String>
    field = mutableListOf()
  val failure = IllegalStateException("Failure at $failureStages")
  val closeFailure = IllegalStateException("Failure closing the cursor")
  private var row = -1
  private val cursor = mock<Cursor>()
  private val db = mock<SupportSQLiteDatabase>()
  private val database = mock<GeneratedDatabase>()

  init {
    whenever(cursor.moveToNext())
      .thenAnswer {
        failAt(MOVE)
        ++row < tableNames.size
      }
    whenever(cursor.getString(0))
      .thenAnswer {
        failAt(READ)
        tableNames[row]
      }
    doAnswer {
      events += "close"
      failAt(CLOSE)
      null
    }
      .whenever(cursor)
      .close()
    whenever(db.query(any<String>()))
      .thenAnswer {
        events += "query"
        cursor
      }
    doAnswer { invocation ->
      events += "drop:${invocation.getArgument<String>(0)}"
      failAt(DROP)
      null
    }
      .whenever(db)
      .execSQL(any())
    doAnswer { invocation ->
      check(invocation.getArgument<SupportSQLiteDatabase>(0) === db)
      events += "create"
      failAt(CREATE)
      null
    }
      .whenever(database)
      .createSchema(any())
  }

  fun downgrade() = DefaultDbDowngrader(database)
    .onDowngrade(
      db = db,
      oldVersion = 7,
      newVersion = 2
    )

  private fun failAt(stage: FailureStage) {
    if (stage in failureStages) {
      throw when (stage) {
        CLOSE -> closeFailure
        else -> failure
      }
    }
  }
}
