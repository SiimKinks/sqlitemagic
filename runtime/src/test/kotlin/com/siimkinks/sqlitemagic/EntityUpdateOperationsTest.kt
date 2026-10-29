package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
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
        .update(TestEntity())
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
}
