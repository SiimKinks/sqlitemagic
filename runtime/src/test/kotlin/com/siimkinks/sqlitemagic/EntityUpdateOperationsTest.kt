package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Test

internal class EntityUpdateOperationsTest {
  @Test
  fun `fixed updates report results and bind selected identity`() {
    data class UpdateCase(
      val label: String,
      val updateResult: Int,
      val conflictAlgorithm: Int,
      val byKey: Boolean,
      val expectedResult: Boolean,
      val expectedSql: String,
      val expectedIdentity: String,
      val expectedTriggers: List<Set<String>>
    )
    listOf(
      UpdateCase(
        label = "default identity success",
        updateResult = 1,
        conflictAlgorithm = CONFLICT_NONE,
        byKey = false,
        expectedResult = true,
        expectedSql = "UPDATE books SET name=? WHERE id=?",
        expectedIdentity = "id-1",
        expectedTriggers = listOf(setOf("books"))
      ),
      UpdateCase(
        label = "default identity no row",
        updateResult = 0,
        conflictAlgorithm = CONFLICT_NONE,
        byKey = false,
        expectedResult = false,
        expectedSql = "UPDATE books SET name=? WHERE id=?",
        expectedIdentity = "id-1",
        expectedTriggers = emptyList()
      ),
      UpdateCase(
        label = "explicit identity success",
        updateResult = 1,
        conflictAlgorithm = CONFLICT_NONE,
        byKey = true,
        expectedResult = true,
        expectedSql = "UPDATE books SET name=? WHERE key=?",
        expectedIdentity = "key-1",
        expectedTriggers = listOf(setOf("books"))
      ),
      UpdateCase(
        label = "ignored conflict success",
        updateResult = 1,
        conflictAlgorithm = CONFLICT_IGNORE,
        byKey = false,
        expectedResult = true,
        expectedSql = "UPDATE OR IGNORE books SET name=? WHERE id=?",
        expectedIdentity = "id-1",
        expectedTriggers = listOf(setOf("books"))
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults += case.updateResult
      val operation = TestAdapter()
        .update(testEntity())
        .usingConnection(connection.connection)
        .conflictAlgorithm(case.conflictAlgorithm)
      val actualResult = when {
        case.byKey -> operation
          .byColumn(TestSchema.key)
          .execute()
        else -> operation.execute()
      }

      assertWithMessage(case.label)
        .that(actualResult)
        .isEqualTo(case.expectedResult)
      assertWithMessage(case.label)
        .that(connection.statementSql)
        .containsExactly(case.expectedSql)
      assertWithMessage(case.label)
        .that(connection.statementBindings)
        .containsExactly(mapOf(1 to "name", 2 to case.expectedIdentity))
      assertWithMessage(case.label)
        .that(connection.triggers)
        .containsExactlyElementsIn(case.expectedTriggers)
    }
  }

  @Test
  fun `Rx terminals complete successful and ignored updates and report failures`() {
    data class RxCase(
      val label: String,
      val updateResult: Int,
      val conflictAlgorithm: Int,
      val expectedError: Boolean,
      val expectedTriggers: List<Set<String>>
    )
    listOf(
      RxCase(
        label = "success",
        updateResult = 1,
        conflictAlgorithm = CONFLICT_NONE,
        expectedError = false,
        expectedTriggers = listOf(setOf("books"))
      ),
      RxCase(
        label = "failure",
        updateResult = 0,
        conflictAlgorithm = CONFLICT_NONE,
        expectedError = true,
        expectedTriggers = emptyList()
      ),
      RxCase(
        label = "ignored",
        updateResult = 0,
        conflictAlgorithm = CONFLICT_IGNORE,
        expectedError = false,
        expectedTriggers = emptyList()
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults += case.updateResult
      val observer = TestAdapter()
        .update(testEntity())
        .usingConnection(connection.connection)
        .conflictAlgorithm(case.conflictAlgorithm)
        .observe()
        .test()

      when {
        case.expectedError -> observer.assertFailure(OperationFailedException::class.java)
        else -> observer.assertResult()
      }
      assertWithMessage(case.label)
        .that(connection.triggers)
        .containsExactlyElementsIn(case.expectedTriggers)
    }
  }

  private fun testEntity() = TestEntity(
    id = "id-1",
    key = "key-1",
    name = "name"
  )
}
