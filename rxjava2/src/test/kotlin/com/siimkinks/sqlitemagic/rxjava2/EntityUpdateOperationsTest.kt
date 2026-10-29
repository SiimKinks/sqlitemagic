package com.siimkinks.sqlitemagic.rxjava2

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.TestEntity
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.newConnection
import com.siimkinks.sqlitemagic.update
import org.junit.Test

internal class EntityUpdateOperationsTest {
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
        .update(TestEntity())
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
}
