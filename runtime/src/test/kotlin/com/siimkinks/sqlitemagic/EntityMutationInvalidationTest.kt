package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class EntityMutationInvalidationTest {
  @Test
  fun `native failures invalidate single entity writes and table deletion`() {
    (MutationOperation.entries - MutationOperation.BULK_INSERT).forEach { operation ->
      val failure = IllegalStateException("SQL failed")
      val fixture = MutationFailureFixture(nativeFailure = failure)
      val actual = assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation.name)
        .that(actual)
        .isSameInstanceAs(failure)
      assertWithMessage(operation.name)
        .that(fixture.triggers)
        .containsExactly(setOf("books"))
    }
  }

  @Test
  fun `binding failures and known zero writes do not invalidate even when cleanup fails`() {
    listOf(MutationOperation.NULL_OMITTING_UPDATE, MutationOperation.DELETE_TABLE).forEach { operation ->
      val fixture = MutationFailureFixture(
        zeroResult = true,
        closeFailure = IllegalStateException("close failed")
      )
      assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation.name)
        .that(fixture.triggers)
        .isEmpty()
    }
    listOf(
      MutationOperation.UPDATE,
      MutationOperation.NULL_OMITTING_UPDATE,
      MutationOperation.INSERT
    ).forEach { operation ->
      val fixture = MutationFailureFixture(bindFailure = IllegalStateException("encoder failed"))
      assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation.name)
        .that(fixture.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `successful writes invalidate when statement cleanup fails`() {
    listOf(MutationOperation.NULL_OMITTING_UPDATE, MutationOperation.DELETE_TABLE).forEach { operation ->
      val fixture = MutationFailureFixture(closeFailure = IllegalStateException("close failed"))
      assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation.name)
        .that(fixture.triggers)
        .containsExactly(setOf("books"))
    }
  }

  @Test
  fun `insert and persist generated id callback failures queue until outer commit or rollback`() {
    listOf(MutationOperation.INSERT, MutationOperation.PERSIST).forEach { operation ->
      listOf(false, true).forEach { commit ->
        val fixture = MutationFailureFixture(idFailure = IllegalStateException("decoder failed"))
        val transaction = fixture.connection.newTransaction()
        try {
          assertThrows(Exception::class.java) {
            fixture.execute(operation)
          }
          assertThat(fixture.triggers)
            .isEmpty()
          if (commit) transaction.markSuccessful()
        } finally {
          transaction.end()
        }
        assertWithMessage("$operation commit=$commit")
          .that(fixture.triggers)
          .isEqualTo(
            when {
              commit -> listOf(setOf("books"))
              else -> emptyList()
            }
          )
      }
    }
  }

  @Test
  fun `native failures are logged once by the failing operation`() {
    MutationOperation.entries.forEach { operation ->
      withLogger(loggingEnabled = true) { logger ->
        val failure = IllegalStateException("SQL failed")
        assertThrows(Exception::class.java) {
          MutationFailureFixture(nativeFailure = failure).execute(operation)
        }
        assertWithMessage(operation.name)
          .that(logger.events.filter { it.level == LogLevel.ERROR })
          .containsExactly(
            LoggedMessage(
              level = LogLevel.ERROR,
              message = "Operation failed",
              throwable = failure
            )
          )
      }
    }
  }

  @Test
  fun `statement compilation failure publishes no triggers`() {
    (MutationOperation.entries - MutationOperation.BULK_INSERT).forEach { operation ->
      val failure = IllegalStateException("compile failed")
      val fixture = MutationFailureFixture(compileFailure = failure)
      val actual = assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation.name)
        .that(actual)
        .isSameInstanceAs(failure)
      assertWithMessage(operation.name)
        .that(fixture.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `native failure queues invalidation until outer transaction commit and discards it on rollback`() {
    (MutationOperation.entries - MutationOperation.BULK_INSERT).forEach { operation ->
      listOf(false, true).forEach { commit ->
        val failure = IllegalStateException("SQL failed")
        val fixture = MutationFailureFixture(nativeFailure = failure)
        val transaction = fixture.connection.newTransaction()
        try {
          val actual = assertThrows(Exception::class.java) {
            fixture.execute(operation)
          }
          assertThat(actual).isSameInstanceAs(failure)
          assertThat(fixture.triggers)
            .isEmpty()
          if (commit) transaction.markSuccessful()
        } finally {
          transaction.end()
        }
        assertWithMessage("$operation commit=$commit")
          .that(fixture.triggers)
          .isEqualTo(
            when {
              commit -> listOf(setOf("books"))
              else -> emptyList()
            }
          )
      }
    }
  }

  @Test
  fun `bulk native and generated id callback failures propagate and roll back without publishing`() {
    mapOf<String, (RuntimeException) -> MutationFailureFixture>(
      "native failure" to { MutationFailureFixture(nativeFailure = it) },
      "generated id callback failure" to { MutationFailureFixture(idFailure = it) }
    ).forEach { (label, fixtureFailing) ->
      val failure = IllegalStateException(label)
      val fixture = fixtureFailing(failure)
      val actual = assertThrows(Exception::class.java) {
        fixture.execute(MutationOperation.BULK_INSERT)
      }
      assertWithMessage(label)
        .that(actual)
        .isSameInstanceAs(failure)
      assertWithMessage(label)
        .that(fixture.triggers)
        .isEmpty()
      assertWithMessage(label)
        .that(fixture.recording.rolledBackTransactions)
        .isEqualTo(1)
    }
  }

  @Test
  fun `trigger delivery failure is suppressed on the original mutation error`() {
    val fixture = MutationFailureFixture()
    val mutationFailure = IllegalStateException("mutation failed")
    val deliveryFailure = IllegalArgumentException("delivery failed")
    fixture.connection.triggers.subscribe(object : Observer<Set<String>> {
      override fun onSubscribe(disposable: Disposable) = Unit
      override fun onNext(tables: Set<String>) = throw deliveryFailure
      override fun onError(error: Throwable) = Unit
      override fun onComplete() = Unit
    })
    val context = mutationContext(fixture)
    val actual = assertThrows(IllegalStateException::class.java) {
      context.executeMutation(
        adapter = fixture.adapter,
        operation = {
          executeUpdateDelete(changedRow())
          throw mutationFailure
        }
      )
    }
    assertThat(actual)
      .isSameInstanceAs(mutationFailure)
    assertThat(actual.suppressed.toList())
      .containsExactly(deliveryFailure)
  }

  @Test
  fun `child trigger suppression applies to success but error invalidation bypasses it`() {
    val fixture = MutationFailureFixture()
    val child = mutationContext(fixture).childWithoutTableTriggers()
    child.sendTableTriggers(fixture.adapter)
    assertThat(fixture.triggers)
      .isEmpty()
    val failure = IllegalStateException("callback failed")
    val actual = assertThrows(IllegalStateException::class.java) {
      child.executeMutation(
        adapter = fixture.adapter,
        operation = {
          executeUpdateDelete(changedRow())
          throw failure
        }
      )
    }
    assertThat(actual)
      .isSameInstanceAs(failure)
    assertThat(fixture.triggers)
      .containsExactly(setOf("books"))
  }

  @Test
  fun `recursive error invalidation includes the complete adapter table closure`() {
    val fixture = MutationFailureFixture()
    val adapter = object : EntityRecursiveAdapter<TestEntity>, EntityAdapter<TestEntity> by fixture.adapter {
      override val triggerTableNames = arrayOf("books", "authors", "publishers")
      override fun insertRelationships(
        entity: TestEntity,
        operations: EntityRelationshipOperations
      ) = true

      override fun updateRelationships(
        entity: TestEntity,
        operations: EntityRelationshipOperations
      ) = true

      override fun persistRelationships(
        entity: TestEntity,
        operations: EntityRelationshipOperations
      ) = true
    }
    val failure = IllegalStateException("callback failed")
    val actual = assertThrows(IllegalStateException::class.java) {
      mutationContext(fixture).executeMutation(
        adapter = adapter,
        operation = {
          executeUpdateDelete(changedRow())
          throw failure
        }
      )
    }
    assertThat(actual)
      .isSameInstanceAs(failure)
    assertThat(fixture.triggers)
      .containsExactly(setOf("books", "authors", "publishers"))
  }

  private fun changedRow() = mock<SupportSQLiteStatement> {
    on { executeUpdateDelete() } doReturn 1
  }

  private fun mutationContext(fixture: MutationFailureFixture) = OperationContext(
    adapter = fixture.adapter,
    configuration = OperationConfigurationSnapshot(connection = fixture.connection)
  )
}

private class MutationFailureFixture(
  nativeFailure: RuntimeException? = null,
  closeFailure: RuntimeException? = null,
  bindFailure: RuntimeException? = null,
  idFailure: RuntimeException? = null,
  compileFailure: RuntimeException? = null,
  zeroResult: Boolean = false
) {
  val recording = RecordingDatabase()
  private val database = object : SupportSQLiteDatabase by recording {
    override fun compileStatement(sql: String): SupportSQLiteStatement {
      compileFailure?.let { throw it }
      val statement = recording.compileStatement(sql)
      return object : SupportSQLiteStatement by statement {
        override fun bindString(index: Int, value: String) {
          bindFailure?.let { throw it }
          statement.bindString(
            index = index,
            value = value
          )
        }

        override fun executeInsert(): Long {
          nativeFailure?.let { throw it }
          return when {
            zeroResult -> -1L
            else -> 1L
          }
        }

        override fun executeUpdateDelete(): Int {
          nativeFailure?.let { throw it }
          return when {
            zeroResult || sql.startsWith("UPDATE") && idFailure != null -> 0
            else -> 1
          }
        }

        override fun close() {
          closeFailure?.let { throw it }
          statement.close()
        }
      }
    }
  }
  val connection = testDbConnection(database)
  private val recordedTriggers = connection.recordTriggers()
  val triggers: List<Set<String>> get() = recordedTriggers.values()
  private val entity = TestEntity(
    id = "id",
    key = "key",
    name = "value"
  )
  val adapter: EntityDefaultIdentityAdapter<TestEntity> =
    object : EntityDefaultIdentityAdapter<TestEntity> by TestAdapter(), EntityGeneratedIdAdapter<TestEntity> {
      override fun assignGeneratedId(
        entity: TestEntity,
        rowId: Long
      ) {
        idFailure?.let { throw it }
        entity.generatedRowId = rowId
      }
    }

  fun execute(operation: MutationOperation): Any = when (operation) {
    MutationOperation.UPDATE -> adapter
      .update(entity)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .usingConnection(connection)
      .execute()
    MutationOperation.NULL_OMITTING_UPDATE -> adapter
      .persist(entity)
      .ignoreNullValues()
      .usingConnection(connection)
      .execute()
    MutationOperation.INSERT -> adapter
      .insert(entity)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .usingConnection(connection)
      .execute()
    MutationOperation.PERSIST -> adapter
      .persist(entity)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .usingConnection(connection)
      .execute()
    MutationOperation.DELETE_TABLE -> adapter
      .deleteTable()
      .usingConnection(connection)
      .execute()
    MutationOperation.BULK_INSERT -> adapter
      .bulkInsert(listOf(entity))
      .usingConnection(connection)
      .execute()
  }
}

private enum class MutationOperation {
  UPDATE,
  NULL_OMITTING_UPDATE,
  INSERT,
  PERSIST,
  DELETE_TABLE,
  BULK_INSERT
}
