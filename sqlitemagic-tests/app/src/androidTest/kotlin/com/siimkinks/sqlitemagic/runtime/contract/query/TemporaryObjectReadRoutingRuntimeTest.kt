package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EXISTS
import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.MainSessionViewTable.Companion.MAIN_SESSION_VIEW
import com.siimkinks.sqlitemagic.PersistentSourceSessionViewTable.Companion.PERSISTENT_SOURCE_SESSION_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.view.MainSessionView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSourceSessionView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val SESSION_ID = "session-id"
private const val NAME = "Ada"
private const val AUTHOR_ID = 7L
private val SESSION_VALUE = MainSessionValue(
  id = SESSION_ID,
  value = NAME
)

@RunWith(Parameterized::class)
internal class TemporaryObjectReadRoutingRuntimeTest(
  private val case: TemporaryReadCase
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun readOutsideTransactionSeesTemporaryObjects() {
    insertedResult(
      result = SESSION_VALUE
        .insert()
        .execute(),
      modelName = "MainSessionValue"
    )
    insertedResult(
      result = QueryCompositionAuthor(
        id = AUTHOR_ID,
        name = NAME
      )
        .insert()
        .execute(),
      modelName = "QueryCompositionAuthor"
    )

    assertThat(case.read()).isEqualTo(case.expected)
  }

  class TemporaryReadCase(
    private val label: String,
    val expected: Any?,
    val read: () -> Any?
  ) {
    override fun toString() = label
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun cases() = listOf(
      TemporaryReadCase(
        label = "typed temporary table",
        expected = listOf(SESSION_VALUE),
        read = {
          SELECT.FROM(MAIN_SESSION_VALUE)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "first of temporary table",
        expected = SESSION_VALUE,
        read = {
          SELECT.FROM(MAIN_SESSION_VALUE)
            .takeFirst()
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "count of temporary table",
        expected = 1L,
        read = {
          SELECT.FROM(MAIN_SESSION_VALUE)
            .count()
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "temporary table column",
        expected = listOf(NAME),
        read = {
          SELECT.COLUMN(MAIN_SESSION_VALUE.VALUE)
            .FROM(MAIN_SESSION_VALUE)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "temporary view",
        expected = listOf(
          MainSessionView(
            id = SESSION_ID,
            value = NAME
          )
        ),
        read = {
          SELECT.FROM(MAIN_SESSION_VIEW)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "temporary view over a persistent table",
        expected = listOf(
          PersistentSourceSessionView(
            id = AUTHOR_ID,
            name = NAME
          )
        ),
        read = {
          SELECT.FROM(PERSISTENT_SOURCE_SESSION_VIEW)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "persistent table joined with a temporary table",
        expected = listOf(AUTHOR_ID),
        read = {
          SELECT.COLUMN(QUERY_COMPOSITION_AUTHOR.ID)
            .FROM(QUERY_COMPOSITION_AUTHOR)
            .INNER_JOIN(MAIN_SESSION_VALUE)
            .ON(MAIN_SESSION_VALUE.VALUE IS QUERY_COMPOSITION_AUTHOR.NAME)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "temporary table in a subquery",
        expected = listOf(AUTHOR_ID),
        read = {
          SELECT.COLUMN(QUERY_COMPOSITION_AUTHOR.ID)
            .FROM(QUERY_COMPOSITION_AUTHOR)
            .WHERE(EXISTS(SELECT FROM MAIN_SESSION_VALUE))
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "raw query declaring a temporary table",
        expected = listOf(NAME),
        read = {
          SELECT
            .RAW("SELECT value FROM main_session_value")
            .observes(MAIN_SESSION_VALUE)
            .readAs(STRING)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "raw query without declared tables",
        expected = listOf(NAME),
        read = {
          SELECT
            .RAW("SELECT value FROM main_session_value")
            .readAs(STRING)
            .execute()
        }
      ),
      TemporaryReadCase(
        label = "observed temporary table",
        expected = listOf(SESSION_VALUE),
        read = {
          SELECT.FROM(MAIN_SESSION_VALUE)
            .observe()
            .runQueryOnce()
            .blockingGet()
        }
      ),
      TemporaryReadCase(
        label = "observed raw query without declared tables",
        expected = listOf(NAME),
        read = {
          SELECT
            .RAW("SELECT value FROM main_session_value")
            .readAs(STRING)
            .observe()
            .blockingGet()
        }
      )
    )
  }
}
