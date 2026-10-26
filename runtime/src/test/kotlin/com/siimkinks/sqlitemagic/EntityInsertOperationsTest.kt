package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EntityInsertOperationsTest {
  @Test
  fun `insert reports row id, assigns generated id, and maps without rowid`() {
    data class InsertCase(
      val label: String,
      val withoutRowId: Boolean,
      val expectedResult: EntityInsertResult,
      val expectedEntity: TestEntity
    )
    listOf(
      InsertCase(
        label = "rowid",
        withoutRowId = false,
        expectedResult = EntityInsertResult.Inserted(41L),
        expectedEntity = TestEntity(generatedRowId = 41L)
      ),
      InsertCase(
        label = "without rowid",
        withoutRowId = true,
        expectedResult = EntityInsertResult.Inserted(null),
        expectedEntity = TestEntity()
      )
    ).forEach { case ->
      val adapter = TestAdapter(withoutRowId = case.withoutRowId)
      val recording = newConnection()
      when {
        case.withoutRowId -> recording.recordingDatabase.updateResults += 1
        else -> recording.recordingDatabase.insertResults += 41L
      }
      val entity = TestEntity()
      val actualResult = adapter
        .insert(entity)
        .usingConnection(recording.connection)
        .execute()

      assertWithMessage(case.label)
        .that(actualResult)
        .isEqualTo(case.expectedResult)
      assertWithMessage(case.label)
        .that(entity)
        .isEqualTo(case.expectedEntity)
      assertWithMessage(case.label)
        .that(recording.triggers)
        .containsExactly(setOf("books"))
    }
  }

  @Test
  fun `failed and ignored inserts preserve entity and publish no triggers`() {
    data class ConflictCase(
      val label: String,
      val withoutRowId: Boolean,
      val conflictAlgorithm: Int,
      val expectedResult: EntityInsertResult?
    )
    listOf(
      ConflictCase(
        label = "rowid failure",
        withoutRowId = false,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = null
      ),
      ConflictCase(
        label = "rowid ignored",
        withoutRowId = false,
        conflictAlgorithm = CONFLICT_IGNORE,
        expectedResult = EntityInsertResult.Ignored
      ),
      ConflictCase(
        label = "without rowid failure",
        withoutRowId = true,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = null
      ),
      ConflictCase(
        label = "without rowid ignored",
        withoutRowId = true,
        conflictAlgorithm = CONFLICT_IGNORE,
        expectedResult = EntityInsertResult.Ignored
      )
    ).forEach { case ->
      val recording = newConnection()
      when {
        case.withoutRowId -> recording.recordingDatabase.updateResults += 0
        else -> recording.recordingDatabase.insertResults += -1L
      }
      val entity = TestEntity(generatedRowId = 7L)
      val operation = TestAdapter(withoutRowId = case.withoutRowId)
        .insert(entity)
        .usingConnection(recording.connection)
        .conflictAlgorithm(case.conflictAlgorithm)

      when (val expectedResult = case.expectedResult) {
        null -> assertThrows(OperationFailedException::class.java, operation::execute)
        else -> assertWithMessage(case.label)
          .that(operation.execute())
          .isEqualTo(expectedResult)
      }
      assertWithMessage(case.label)
        .that(entity)
        .isEqualTo(TestEntity(generatedRowId = 7L))
      assertWithMessage(case.label)
        .that(recording.recordingDatabase.rolledBackTransactions)
        .isEqualTo(0)
      assertWithMessage(case.label)
        .that(recording.triggers)
        .isEmpty()
    }
  }
}
