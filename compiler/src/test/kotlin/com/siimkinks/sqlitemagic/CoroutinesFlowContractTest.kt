package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class CoroutinesFlowContractTest {
  @Test
  fun `every observed shape resolves to its operators and result types`() {
    compileFlowConsumer(
      """
        fun verify(defaultCursor: Cursor) {
          val rows = SELECT.COLUMN(PERSON.NAME).FROM(PERSON).observe()
          val first = SELECT.COLUMN(PERSON.NAME).FROM(PERSON).takeFirst().observe()
          val firstParent = SELECT.COLUMN(PERSON.PARENT_ID).FROM(PERSON).takeFirst().observe()
          val count = SELECT.COLUMN(PERSON.ID).FROM(PERSON).count().observe()
          val cursor = SELECT.COLUMN(PERSON.ID).FROM(PERSON).toCursor().observe()

          val exactRowsStream: Exact<Flow<Query<List<String?>>>> = rows.exactType()
          val exactFirstStream: Exact<Flow<Query<String?>>> = first.exactType()
          val exactCountStream: Exact<Flow<Query<Long>>> = count.exactType()
          val exactCursorStream: Exact<Flow<Query<Cursor>>> = cursor.exactType()
          val exactRows: Exact<Flow<List<String?>>> = rows.runQuery().exactType()
          val exactFirst: Exact<Flow<String>> = first.runQuery().exactType()
          val exactFirstOrDefault: Exact<Flow<String>> = first.runQueryOrDefault("none").exactType()
          val exactFirstParent: Exact<Flow<Long>> = firstParent.runQuery().exactType()
          val exactCount: Exact<Flow<Long>> = count.runQuery().exactType()
          val exactCountOrDefault: Exact<Flow<Long>> = count.runQueryOrDefault(0L).exactType()
          val exactZero: Exact<Flow<Boolean>> = count.isZero().exactType()
          val exactNotZero: Exact<Flow<Boolean>> = count.isNotZero().exactType()
          val exactCursor: Exact<Flow<Cursor>> = cursor.runQuery().exactType()
          val exactCursorOrDefault: Exact<Flow<Cursor>> = cursor.runQueryOrDefault(defaultCursor).exactType()
        }

        suspend fun verifyOnce(
          rows: Flow<Query<List<String?>>>,
          first: Flow<Query<String?>>,
          count: Flow<Query<Long>>,
          cursor: Flow<Query<Cursor>>,
          defaultCursor: Cursor
        ) {
          val exactRows: Exact<List<String?>> = rows.runQueryOnce().exactType()
          val exactFirst: Exact<String?> = first.runQueryOnce().exactType()
          val exactFirstOrDefault: Exact<String> = first.runQueryOnceOrDefault("none").exactType()
          val exactCount: Exact<Long?> = count.runQueryOnce().exactType()
          val exactCountOrDefault: Exact<Long> = count.runQueryOnceOrDefault(0L).exactType()
          val exactCursor: Exact<Cursor?> = cursor.runQueryOnce().exactType()
          val exactCursorOrDefault: Exact<Cursor> = cursor.runQueryOnceOrDefault(defaultCursor).exactType()
          val exactRun: Exact<List<String?>> = rows.first().run().exactType()
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrograms")
  fun `operators outside their shape do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileFlowConsumer(
      """
        suspend fun verify(
          rows: Flow<Query<List<String?>>>,
          first: Flow<Query<String?>>,
          firstParent: Flow<Query<Long?>>
        ) {
          $program
        }
      """
    ).assertCompilationError(diagnostic)
  }

  private fun compileFlowConsumer(body: String) = compileExpressionConsumer(
    body = body,
    imports = """
      import android.database.Cursor
      import com.siimkinks.sqlitemagic.Query
      import com.siimkinks.sqlitemagic.coroutines.isNotZero
      import com.siimkinks.sqlitemagic.coroutines.isZero
      import com.siimkinks.sqlitemagic.coroutines.observe
      import com.siimkinks.sqlitemagic.coroutines.run
      import com.siimkinks.sqlitemagic.coroutines.runQuery
      import com.siimkinks.sqlitemagic.coroutines.runQueryOnce
      import com.siimkinks.sqlitemagic.coroutines.runQueryOnceOrDefault
      import com.siimkinks.sqlitemagic.coroutines.runQueryOrDefault
      import kotlinx.coroutines.flow.Flow
      import kotlinx.coroutines.flow.first
    """
  )

  companion object {
    @JvmStatic
    fun invalidPrograms() = listOf(
      Arguments.of(
        "single-row default on a list stream",
        "rows.runQueryOrDefault(emptyList<String?>())",
        "receiver type mismatch"
      ),
      Arguments.of(
        "single-row one-shot default on a list stream",
        "rows.runQueryOnceOrDefault(emptyList<String?>())",
        "receiver type mismatch"
      ),
      Arguments.of(
        "count predicate on a first-row Long? stream",
        "firstParent.isZero()",
        "receiver type mismatch"
      ),
      Arguments.of(
        "count predicate on a list stream",
        "rows.isNotZero()",
        "receiver type mismatch"
      ),
      Arguments.of(
        "null default",
        "first.runQueryOrDefault(null)",
        "Null cannot be a value of a non-null type"
      )
    )
  }
}
