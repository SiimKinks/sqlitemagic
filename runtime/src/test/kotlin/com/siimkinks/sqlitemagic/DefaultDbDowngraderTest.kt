package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.FailureStage.CLOSE
import com.siimkinks.sqlitemagic.FailureStage.CREATE
import com.siimkinks.sqlitemagic.FailureStage.DROP
import com.siimkinks.sqlitemagic.FailureStage.MOVE
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
        """drop:DROP TABLE IF EXISTS "first_table"""",
        """drop:DROP TABLE IF EXISTS "second_table"""",
        "close",
        "create"
      )
      .inOrder()
  }

  @Test
  fun `downgrade drops the newer version's views and quoted tables before recreating the schema`() {
    val names = BundledSqliteDatabase().use { db ->
      db.execSQL("CREATE TABLE android_metadata (locale TEXT)")
      db.execSQL("""CREATE TABLE "newer item" (id INTEGER)""")
      db.execSQL("""CREATE VIEW newer_view AS SELECT id FROM "newer item"""")
      val database = mock<GeneratedDatabase>()
      doAnswer {
        db.execSQL("CREATE TABLE item (id INTEGER)")
      }
        .whenever(database)
        .createSchema(any())

      DefaultDbDowngrader(database).onDowngrade(
        db = db,
        oldVersion = 7,
        newVersion = 2
      )
      db.query("SELECT name FROM sqlite_master ORDER BY name").use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(cursor.getString(0))
          }
        }
      }
    }

    assertThat(names).containsExactly("android_metadata", "item")
      .inOrder()
  }

  @Test
  fun `failures propagate after closing the cursor and stop the downgrade`() {
    data class FailureCase(
      val stages: Set<FailureStage>,
      val tableNames: List<String> = listOf("first_table", "second_table"),
      val thrown: (DowngradeFixture) -> IllegalStateException = DowngradeFixture::failure,
      val suppressed: (DowngradeFixture) -> List<IllegalStateException> = { emptyList() },
      val expectedEvents: List<String> = listOf("query", "close")
    )
    listOf(
      FailureCase(stages = setOf(MOVE)),
      FailureCase(
        stages = setOf(DROP),
        expectedEvents = listOf("query", """drop:DROP TABLE IF EXISTS "first_table"""", "close")
      ),
      FailureCase(
        stages = setOf(MOVE, CLOSE),
        suppressed = { listOf(it.closeFailure) }
      ),
      FailureCase(
        stages = setOf(CLOSE),
        tableNames = emptyList(),
        thrown = DowngradeFixture::closeFailure
      ),
      FailureCase(
        stages = setOf(CREATE),
        tableNames = emptyList(),
        expectedEvents = listOf("query", "close", "create")
      )
    ).forEach { case ->
      val label = case.stages.joinToString()
      val fixture = DowngradeFixture(
        tableNames = case.tableNames,
        failureStages = case.stages
      )

      val failure = assertThrows(
        IllegalStateException::class.java,
        fixture::downgrade
      )

      assertWithMessage(label)
        .that(failure)
        .isSameInstanceAs(case.thrown(fixture))
      assertWithMessage(label)
        .that(failure.suppressed.toList())
        .containsExactlyElementsIn(case.suppressed(fixture))
      assertWithMessage(label)
        .that(fixture.events)
        .containsExactlyElementsIn(case.expectedEvents)
        .inOrder()
    }
  }
}

private enum class FailureStage {
  MOVE,
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
      .thenReturn("TABLE")
    whenever(cursor.getString(1))
      .thenAnswer { tableNames[row] }
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
