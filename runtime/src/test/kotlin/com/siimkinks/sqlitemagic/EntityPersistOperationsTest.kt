package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Test

internal class EntityPersistOperationsTest {
  @Test
  fun `persist maps update and insert outcomes with correct side effects`() {
    data class PersistCase(
      val label: String,
      val withoutRowId: Boolean,
      val updateResults: List<Int>,
      val insertResult: Long?,
      val conflictAlgorithm: Int,
      val expectedResult: EntityPersistResult?,
      val expectedGeneratedRowId: Long?,
      val expectedTriggers: List<Set<String>>
    )
    listOf(
      PersistCase(
        label = "updated",
        withoutRowId = false,
        updateResults = listOf(1),
        insertResult = null,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = EntityPersistResult.Updated,
        expectedGeneratedRowId = 5L,
        expectedTriggers = listOf(setOf("books"))
      ),
      PersistCase(
        label = "inserted",
        withoutRowId = false,
        updateResults = listOf(0),
        insertResult = 7L,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = EntityPersistResult.Inserted(7L),
        expectedGeneratedRowId = 7L,
        expectedTriggers = listOf(setOf("books"))
      ),
      PersistCase(
        label = "ignored",
        withoutRowId = false,
        updateResults = listOf(0),
        insertResult = -1L,
        conflictAlgorithm = CONFLICT_IGNORE,
        expectedResult = EntityPersistResult.Ignored,
        expectedGeneratedRowId = 5L,
        expectedTriggers = emptyList()
      ),
      PersistCase(
        label = "failure",
        withoutRowId = false,
        updateResults = listOf(0),
        insertResult = -1L,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = null,
        expectedGeneratedRowId = 5L,
        expectedTriggers = emptyList()
      ),
      PersistCase(
        label = "without rowid inserted",
        withoutRowId = true,
        updateResults = listOf(0, 1),
        insertResult = null,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = EntityPersistResult.Inserted(null),
        expectedGeneratedRowId = 5L,
        expectedTriggers = listOf(setOf("books"))
      ),
      PersistCase(
        label = "without rowid ignored",
        withoutRowId = true,
        updateResults = listOf(0, 0),
        insertResult = null,
        conflictAlgorithm = CONFLICT_IGNORE,
        expectedResult = EntityPersistResult.Ignored,
        expectedGeneratedRowId = 5L,
        expectedTriggers = emptyList()
      ),
      PersistCase(
        label = "without rowid failure",
        withoutRowId = true,
        updateResults = listOf(0, 0),
        insertResult = null,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = null,
        expectedGeneratedRowId = 5L,
        expectedTriggers = emptyList()
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults.addAll(case.updateResults)
      case.insertResult?.let(connection.recordingDatabase.insertResults::add)
      val entity = testEntity(generatedRowId = 5L)
      val operation = TestAdapter(withoutRowId = case.withoutRowId)
        .persist(entity)
        .usingConnection(connection.connection)
        .conflictAlgorithm(case.conflictAlgorithm)

      when (val expectedResult = case.expectedResult) {
        null -> assertSingleOperationFailure(operation::execute)
        else -> assertWithMessage(case.label)
          .that(operation.execute())
          .isEqualTo(expectedResult)
      }
      assertWithMessage(case.label)
        .that(entity)
        .isEqualTo(testEntity(generatedRowId = case.expectedGeneratedRowId))
      assertWithMessage(case.label)
        .that(connection.triggers)
        .containsExactlyElementsIn(case.expectedTriggers)
    }
  }

  @Test
  fun `persist skips update without selected identity and supports explicit identity`() {
    val missingDefaultConnection = newConnection()
    missingDefaultConnection.recordingDatabase.insertResults += 8L
    val missingDefaultEntity = TestEntity(
      id = "",
      key = "key-1",
      name = "name"
    )

    assertThat(
      TestAdapter()
        .persist(missingDefaultEntity)
        .usingConnection(missingDefaultConnection.connection)
        .execute()
    ).isEqualTo(EntityPersistResult.Inserted(8L))
    assertThat(missingDefaultConnection.statementSql).containsExactly(
      "INSERT INTO books (id, key, name) VALUES (?, ?, ?)"
    )

    val explicitConnection = newConnection()
    explicitConnection.recordingDatabase.updateResults += 1
    val explicitEntity = TestEntity(
      id = "",
      key = "key-1",
      name = "name"
    )

    assertThat(
      TestAdapter()
        .persist(explicitEntity)
        .usingConnection(explicitConnection.connection)
        .byColumn(TestSchema.key)
        .execute()
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(explicitConnection.statementSql).containsExactly("UPDATE books SET name=? WHERE key=?")
    assertThat(explicitConnection.statementBindings).containsExactly(mapOf(1 to "name", 2 to "key-1"))
  }

  @Test
  fun `ignoring null values uses update no-op, variable insert, and default insert statements`() {
    data class NullOmissionCase(
      val label: String,
      val omitAllInsertValues: Boolean,
      val updateResult: Int,
      val insertResult: Long?,
      val byKey: Boolean,
      val expectedResult: EntityPersistResult,
      val expectedSql: List<String>,
      val expectedBindings: List<Map<Int, Any?>>
    )
    listOf(
      NullOmissionCase(
        label = "default identity update",
        omitAllInsertValues = false,
        updateResult = 1,
        insertResult = null,
        byKey = false,
        expectedResult = EntityPersistResult.Updated,
        expectedSql = listOf("UPDATE books SET id=id WHERE id=?"),
        expectedBindings = listOf(mapOf(1 to "id-1"))
      ),
      NullOmissionCase(
        label = "explicit identity update",
        omitAllInsertValues = false,
        updateResult = 1,
        insertResult = null,
        byKey = true,
        expectedResult = EntityPersistResult.Updated,
        expectedSql = listOf("UPDATE books SET key=key WHERE key=?"),
        expectedBindings = listOf(mapOf(1 to "key-1"))
      ),
      NullOmissionCase(
        label = "variable insert",
        omitAllInsertValues = false,
        updateResult = 0,
        insertResult = 9L,
        byKey = false,
        expectedResult = EntityPersistResult.Inserted(9L),
        expectedSql = listOf(
          "UPDATE books SET id=id WHERE id=?",
          "INSERT INTO books(id,key) VALUES (?,?)"
        ),
        expectedBindings = listOf(
          mapOf(1 to "id-1"),
          mapOf(1 to "id-1", 2 to "key-1")
        )
      ),
      NullOmissionCase(
        label = "default insert",
        omitAllInsertValues = true,
        updateResult = 0,
        insertResult = 10L,
        byKey = false,
        expectedResult = EntityPersistResult.Inserted(10L),
        expectedSql = listOf(
          "UPDATE books SET id=id WHERE id=?",
          "INSERT INTO books DEFAULT VALUES"
        ),
        expectedBindings = listOf(
          mapOf(1 to "id-1"),
          emptyMap()
        )
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults += case.updateResult
      case.insertResult?.let(connection.recordingDatabase.insertResults::add)
      val operation = TestAdapter(omitNullInsertValues = case.omitAllInsertValues)
        .persist(testEntity(name = null))
        .usingConnection(connection.connection)
        .ignoreNullValues()
      val actualResult = when {
        case.byKey -> operation.byColumn(TestSchema.key)
          .execute()
        else -> operation.execute()
      }

      assertWithMessage(case.label)
        .that(actualResult)
        .isEqualTo(case.expectedResult)
      assertWithMessage(case.label)
        .that(connection.statementSql)
        .containsExactlyElementsIn(case.expectedSql)
        .inOrder()
      assertWithMessage(case.label)
        .that(connection.statementBindings)
        .containsExactlyElementsIn(case.expectedBindings)
        .inOrder()
      assertWithMessage(case.label)
        .that(connection.triggers)
        .containsExactly(setOf("books"))
    }
  }

  @Test
  fun `reused persist builder rebuilds variable statement when conflict changes`() {
    val connection = newConnection()
    connection.recordingDatabase.updateResults.addAll(listOf(0, 0))
    connection.recordingDatabase.insertResults.addAll(listOf(7L, -1L))
    val entity = testEntity(name = null)
    val builder = TestAdapter()
      .persist(entity)
      .usingConnection(connection.connection)
      .ignoreNullValues()

    assertThat(builder.execute()).isEqualTo(EntityPersistResult.Inserted(rowId = 7L))
    assertThat(
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityPersistResult.Ignored)
    assertThat(connection.statementSql)
      .containsExactly(
        "UPDATE books SET id=id WHERE id=?",
        "INSERT INTO books(id,key) VALUES (?,?)",
        "UPDATE OR IGNORE books SET id=id WHERE id=?",
        "INSERT OR IGNORE INTO books(id,key) VALUES (?,?)"
      )
      .inOrder()
    assertThat(entity).isEqualTo(testEntity(name = null, generatedRowId = 7L))
  }

  @Test
  fun `Rx terminals emit updated inserted and ignored results and report failures`() {
    data class RxCase(
      val label: String,
      val updateResult: Int,
      val insertResult: Long?,
      val conflictAlgorithm: Int,
      val expectedResult: EntityPersistResult?,
      val expectedTriggers: List<Set<String>>
    )
    listOf(
      RxCase(
        label = "updated",
        updateResult = 1,
        insertResult = null,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = EntityPersistResult.Updated,
        expectedTriggers = listOf(setOf("books"))
      ),
      RxCase(
        label = "inserted",
        updateResult = 0,
        insertResult = 11L,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = EntityPersistResult.Inserted(11L),
        expectedTriggers = listOf(setOf("books"))
      ),
      RxCase(
        label = "ignored",
        updateResult = 0,
        insertResult = -1L,
        conflictAlgorithm = CONFLICT_IGNORE,
        expectedResult = EntityPersistResult.Ignored,
        expectedTriggers = emptyList()
      ),
      RxCase(
        label = "failure",
        updateResult = 0,
        insertResult = -1L,
        conflictAlgorithm = CONFLICT_NONE,
        expectedResult = null,
        expectedTriggers = emptyList()
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults += case.updateResult
      case.insertResult?.let(connection.recordingDatabase.insertResults::add)
      val observer = TestAdapter()
        .persist(testEntity())
        .usingConnection(connection.connection)
        .conflictAlgorithm(case.conflictAlgorithm)
        .observe()
        .test()

      when (val expectedResult = case.expectedResult) {
        null -> observer.assertFailure(OperationFailedException::class.java)
        else -> observer.assertResult(expectedResult)
      }
      assertWithMessage(case.label)
        .that(connection.triggers)
        .containsExactlyElementsIn(case.expectedTriggers)
    }
  }

  @Test
  fun `observed terminal snapshots null omission configuration`() {
    val connection = newConnection()
    connection.recordingDatabase.updateResults.addAll(listOf(1, 1))
    val builder = TestAdapter()
      .persist(testEntity(name = null))
      .usingConnection(connection.connection)
    val fixedValues = builder.observe()

    builder.ignoreNullValues()
    fixedValues
      .test()
      .assertResult(EntityPersistResult.Updated)
    builder
      .observe()
      .test()
      .assertResult(EntityPersistResult.Updated)

    assertThat(connection.statementSql)
      .containsExactly(
        "UPDATE books SET name=? WHERE id=?",
        "UPDATE books SET id=id WHERE id=?"
      )
      .inOrder()
  }

  @Test
  fun `each observed subscription receives isolated execution scratch state`() {
    val connection = newConnection()
    connection.recordingDatabase.updateResults.addAll(listOf(0, 0))
    connection.recordingDatabase.insertResults.addAll(listOf(7L, 8L))
    val adapter = TestAdapter()
    val persist = adapter
      .persist(testEntity(name = null))
      .usingConnection(connection.connection)
      .ignoreNullValues()
      .observe()

    persist
      .test()
      .assertResult(EntityPersistResult.Inserted(rowId = 7L))
    persist
      .test()
      .assertResult(EntityPersistResult.Inserted(rowId = 8L))

    assertThat(adapter.bindMaps).hasSize(2)
    assertThat(adapter.bindMaps[0]).isNotSameInstanceAs(adapter.bindMaps[1])
  }

  private fun testEntity(
    name: String? = "name",
    generatedRowId: Long? = null
  ) = TestEntity(
    id = "id-1",
    key = "key-1",
    name = name,
    generatedRowId = generatedRowId
  )
}
