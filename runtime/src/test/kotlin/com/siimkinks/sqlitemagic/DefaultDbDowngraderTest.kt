package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.FailureStage.CLOSE
import com.siimkinks.sqlitemagic.FailureStage.CREATE
import com.siimkinks.sqlitemagic.FailureStage.DROP
import com.siimkinks.sqlitemagic.FailureStage.STEP
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class DefaultDbDowngraderTest {
  @Test
  fun `downgrade reads every returned table, then drops them in order and recreates the schema`() {
    val fixture = DowngradeFixture(tableNames = listOf("first_table", "second_table"))

    fixture.downgrade()

    assertThat(fixture.events)
      .containsExactly(
        "query",
        "close",
        """drop:DROP TABLE IF EXISTS "first_table"""",
        """drop:DROP TABLE IF EXISTS "second_table"""",
        "create"
      )
      .inOrder()
  }

  @Test
  fun `downgrade drops the newer version's views and quoted tables before recreating the schema`() {
    val names = BundledSQLiteDriver()
      .open(MEMORY_DATABASE)
      .use { connection ->
        connection.execSQL("CREATE TABLE android_metadata (locale TEXT)")
        connection.execSQL("""CREATE TABLE "newer item" (id INTEGER)""")
        connection.execSQL("""CREATE VIEW newer_view AS SELECT id FROM "newer item"""")

        DefaultDbDowngrader(
          TestGeneratedDatabase(
            tableCount = 1,
            schema = listOf("CREATE TABLE item (id INTEGER)")
          )
        ).onDowngrade(
          connection = connection,
          oldVersion = 7,
          newVersion = 2
        )
        connection.query("SELECT name FROM sqlite_master ORDER BY name") { it.getText(0) }
      }

    assertThat(names)
      .containsExactly("android_metadata", "item")
      .inOrder()
  }

  @Test
  fun `failures propagate after closing the schema query and stop the downgrade`() {
    data class FailureCase(
      val stages: Set<FailureStage>,
      val tableNames: List<String> = listOf("first_table", "second_table"),
      val thrown: (DowngradeFixture) -> IllegalStateException = DowngradeFixture::failure,
      val suppressed: (DowngradeFixture) -> List<IllegalStateException> = { emptyList() },
      val expectedEvents: List<String> = listOf("query", "close")
    )
    listOf(
      FailureCase(stages = setOf(STEP)),
      FailureCase(
        stages = setOf(DROP),
        expectedEvents = listOf("query", "close", """drop:DROP TABLE IF EXISTS "first_table"""")
      ),
      FailureCase(
        stages = setOf(STEP, CLOSE),
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

      val failure = assertThrows(IllegalStateException::class.java, fixture::downgrade)

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
  STEP,
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
  val closeFailure = IllegalStateException("Failure closing the schema query")
  private var row = -1
  private val query = mock<SQLiteStatement>()
  private val connection = mock<SQLiteConnection>()
  private val database = mock<GeneratedDatabase>()

  init {
    whenever(query.step())
      .thenAnswer {
        failAt(STEP)
        ++row < tableNames.size
      }
    whenever(query.getText(0))
      .thenReturn("TABLE")
    whenever(query.getText(1))
      .thenAnswer { tableNames[row] }
    doAnswer {
      events += "close"
      failAt(CLOSE)
      null
    }
      .whenever(query)
      .close()
    whenever(connection.prepare(any()))
      .thenAnswer { invocation ->
        val sql = invocation.getArgument<String>(0)
        when {
          sql.startsWith("SELECT") -> {
            events += "query"
            query
          }
          else -> mock<SQLiteStatement>().also { drop ->
            whenever(drop.step())
              .thenAnswer {
                events += "drop:$sql"
                failAt(DROP)
                false
              }
          }
        }
      }
    doAnswer { invocation ->
      check(invocation.getArgument<SQLiteConnection>(0) === connection)
      events += "create"
      failAt(CREATE)
      null
    }
      .whenever(database)
      .createSchema(any())
  }

  fun downgrade() = DefaultDbDowngrader(database)
    .onDowngrade(
      connection = connection,
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
