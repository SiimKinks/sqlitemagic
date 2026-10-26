package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Assert.assertThrows
import org.junit.Test

internal class RecursiveEntityTransactionTest {
  @Test
  fun `a failed or ignored write rolls back the complete graph without triggers`() {
    data class Case(
      val label: String,
      val updateResults: List<Int> = emptyList(),
      val insertResults: List<Long> = emptyList(),
      val operation: (RecursiveScenario) -> Any,
      val expectedResult: Any?
    )

    listOf(
      Case(
        label = "ignored child insert",
        insertResults = listOf(121L, -1L),
        operation = { scenario ->
          scenario.parent
            .insert(recursiveGraph())
            .usingConnection(scenario.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expectedResult = EntityInsertResult.Ignored
      ),
      Case(
        label = "failed child insert",
        insertResults = listOf(131L, -1L),
        operation = { scenario ->
          scenario.parent
            .insert(recursiveGraph())
            .usingConnection(scenario.connection)
            .execute()
        },
        expectedResult = null
      ),
      Case(
        label = "ignored parent insert after its relationships",
        insertResults = listOf(241L, 242L, -1L),
        operation = { scenario ->
          scenario.parent
            .insert(recursiveGraph())
            .usingConnection(scenario.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expectedResult = EntityInsertResult.Ignored
      ),
      Case(
        label = "ignored child update",
        updateResults = listOf(1, 0),
        operation = { scenario ->
          scenario.parent
            .update(parentWithChild())
            .usingConnection(scenario.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expectedResult = false
      ),
      Case(
        label = "ignored parent persist after its relationships",
        updateResults = listOf(0, 0),
        insertResults = listOf(201L, -1L),
        operation = { scenario ->
          scenario.parent
            .persist(parentWithChild())
            .usingConnection(scenario.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expectedResult = EntityPersistResult.Ignored
      ),
      Case(
        label = "ignored null omitting parent persist after its relationships",
        updateResults = listOf(0, 0),
        insertResults = listOf(321L, -1L),
        operation = { scenario ->
          scenario.parent
            .persist(parentWithChild())
            .usingConnection(scenario.connection)
            .ignoreNullValues()
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expectedResult = EntityPersistResult.Ignored
      )
    ).forEach { case ->
      val scenario = RecursiveScenario()
      scenario.database.updateResults.addAll(case.updateResults)
      scenario.database.insertResults.addAll(case.insertResults)

      when (val expectedResult = case.expectedResult) {
        null -> assertThrows(OperationFailedException::class.java) { case.operation(scenario) }
        else -> assertWithMessage(case.label)
          .that(case.operation(scenario))
          .isEqualTo(expectedResult)
      }
      assertWithMessage(case.label)
        .that(scenario.recordingConnection.transactionResult(Unit))
        .isEqualTo(
          TransactionResult(
            result = Unit,
            committed = 0,
            rolledBack = 1
          )
        )
      assertWithMessage(case.label)
        .that(scenario.triggers)
        .isEmpty()
    }
  }
}
