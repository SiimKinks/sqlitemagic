package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.observers.TestObserver
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
        expectedEntity = TestEntity(
          id = "id-1",
          key = "key-1",
          name = "name",
          generatedRowId = 41L
        )
      ),
      InsertCase(
        label = "without rowid",
        withoutRowId = true,
        expectedResult = EntityInsertResult.Inserted(null),
        expectedEntity = TestEntity(
          id = "id-1",
          key = "key-1",
          name = "name"
        )
      )
    ).forEach { case ->
      val adapter = TestAdapter(withoutRowId = case.withoutRowId)
      val recording = newConnection()
      when {
        case.withoutRowId -> recording.recordingDatabase.updateResults += 1
        else -> recording.recordingDatabase.insertResults += 41L
      }
      val entity = TestEntity(
        id = "id-1",
        key = "key-1",
        name = "name"
      )
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
      val entity = TestEntity(
        id = "id-1",
        key = "key-1",
        name = "name",
        generatedRowId = 7L
      )
      val operation = TestAdapter(withoutRowId = case.withoutRowId)
        .insert(entity)
        .usingConnection(recording.connection)
        .conflictAlgorithm(case.conflictAlgorithm)

      when (val expectedResult = case.expectedResult) {
        null -> assertSingleOperationFailure(operation::execute)
        else -> assertWithMessage(case.label)
          .that(operation.execute())
          .isEqualTo(expectedResult)
      }
      assertWithMessage(case.label)
        .that(entity)
        .isEqualTo(
          TestEntity(
            id = "id-1",
            key = "key-1",
            name = "name",
            generatedRowId = 7L
          )
        )
      assertWithMessage(case.label)
        .that(recording.recordingDatabase.rolledBackTransactions)
        .isEqualTo(0)
      assertWithMessage(case.label)
        .that(recording.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `single and bulk operations use explicit connections`() {
    val singleConnection = newConnection()
    singleConnection.recordingDatabase.insertResults += 3L
    val adapter = TestAdapter()
    val entity = TestEntity(
      id = "id-1",
      key = "key-1",
      name = "name"
    )

    val singleResult = adapter
      .insert(entity)
      .usingConnection(singleConnection.connection)
      .execute()
    assertThat(singleResult).isEqualTo(EntityInsertResult.Inserted(3L))
    assertThat(singleConnection.recordingDatabase.compiledStatements).isNotEmpty()

    val bulkConnection = newConnection()
    bulkConnection.recordingDatabase.insertResults.addAll(listOf(4L, 5L, 6L))
    assertThat(
      adapter
        .bulkInsert(listOf(entity, entity, entity))
        .usingConnection(bulkConnection.connection)
        .execute()
    ).isTrue()
    assertThat(bulkConnection.recordingDatabase.successfulTransactions).isEqualTo(1)
    assertThat(bulkConnection.recordingDatabase.compiledStatements).isNotEmpty()
  }

  @Test
  fun `single Rx terminals deliver results and errors`() {
    val success = newConnection()
    success.recordingDatabase.insertResults += 11L
    TestAdapter()
      .insert(
        TestEntity(
          id = "id-1",
          key = "key-1",
          name = "name"
        )
      )
      .usingConnection(success.connection)
      .observe()
      .test()
      .assertResult(EntityInsertResult.Inserted(rowId = 11L))

    val error = newConnection()
    error.recordingDatabase.insertResults += -1L
    TestAdapter()
      .insert(
        TestEntity(
          id = "id-1",
          key = "key-1",
          name = "name"
        )
      )
      .usingConnection(error.connection)
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
  }

  @Test
  fun `bulk Rx terminals deliver completion and errors`() {
    val success = newConnection()
    success.recordingDatabase.insertResults.addAll(listOf(1L, 2L))
    TestAdapter()
      .bulkInsert(testEntities())
      .usingConnection(success.connection)
      .observe()
      .test()
      .assertResult()
    assertThat(success.recordingDatabase.successfulTransactions).isEqualTo(1)

    val error = newConnection()
    error.recordingDatabase.insertResults.addAll(listOf(1L, -1L))
    TestAdapter()
      .bulkInsert(testEntities())
      .usingConnection(error.connection)
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertThat(error.recordingDatabase.rolledBackTransactions).isEqualTo(1)
  }

  @Test
  fun `disposed bulk Rx rolls partial work back silently`() {
    val connection = newConnection()
    connection.recordingDatabase.insertResults += 1L
    val observer = TestObserver<Void>()
    val entities = object : Iterable<TestEntity> {
      private val values = testEntities()

      override fun iterator() = object : Iterator<TestEntity> {
        private var index = 0

        override fun hasNext(): Boolean {
          if (index == 1) {
            observer.dispose()
          }
          return index < values.size
        }

        override fun next() = values[index++]
      }
    }

    TestAdapter()
      .bulkInsert(entities)
      .usingConnection(connection.connection)
      .observe()
      .subscribe(observer)

    observer.assertEmpty()
    assertThat(connection.recordingDatabase.successfulTransactions).isEqualTo(0)
    assertThat(connection.recordingDatabase.rolledBackTransactions).isEqualTo(1)
    assertThat(connection.triggers).isEmpty()
  }

  private fun testEntities() = listOf(
    TestEntity(
      id = "id-1",
      key = "key-1",
      name = "name"
    ),
    TestEntity(
      id = "id-2",
      key = "key-2",
      name = "name"
    )
  )
}
