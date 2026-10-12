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
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EntityMutationInvalidationTest {
  @Test
  fun `native failures invalidate single entity writes and table deletion`() {
    listOf("update", "null omitting update", "insert", "persist", "delete table").forEach { operation ->
      val failure = IllegalStateException("SQL failed")
      val fixture = MutationFailureFixture(nativeFailure = failure)
      val actual = assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation)
        .that(actual === failure || actual.cause === failure)
        .isTrue()
      assertWithMessage(operation)
        .that(fixture.triggers)
        .containsExactly(setOf("books"))
    }
  }

  @Test
  fun `binding failures and known zero writes do not invalidate even when cleanup fails`() {
    listOf("null omitting update", "delete table").forEach { operation ->
      val fixture = MutationFailureFixture(
        zeroResult = true,
        closeFailure = IllegalStateException("close failed")
      )
      assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation)
        .that(fixture.triggers)
        .isEmpty()
    }
    listOf("update", "insert", "persist", "delete table").forEach { operation ->
      val fixture = MutationFailureFixture(zeroResult = true)
      fixture.execute(operation)
      assertWithMessage(operation)
        .that(fixture.triggers)
        .isEmpty()
    }
    listOf("update", "null omitting update", "insert").forEach { operation ->
      val fixture = MutationFailureFixture(bindFailure = IllegalStateException("encoder failed"))
      assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation)
        .that(fixture.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `successful writes invalidate when statement cleanup fails`() {
    listOf("null omitting update", "delete table").forEach { operation ->
      val fixture = MutationFailureFixture(closeFailure = IllegalStateException("close failed"))
      assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation)
        .that(fixture.triggers)
        .containsExactly(setOf("books"))
    }
  }

  @Test
  fun `insert and persist generated id callback failures queue until outer commit or rollback`() {
    listOf("insert", "persist").forEach { operation ->
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
  fun `statement compilation failure publishes no triggers`() {
    listOf("update", "null omitting update", "insert", "persist", "delete table").forEach { operation ->
      val failure = IllegalStateException("compile failed")
      val fixture = MutationFailureFixture(compileFailure = failure)
      val actual = assertThrows(Exception::class.java) {
        fixture.execute(operation)
      }
      assertWithMessage(operation)
        .that(actual === failure || actual.cause === failure)
        .isTrue()
      assertWithMessage(operation)
        .that(fixture.nativeExecutions)
        .isEqualTo(0)
      assertWithMessage(operation)
        .that(fixture.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `native failure queues invalidation until outer transaction commit and discards it on rollback`() {
    listOf("update", "null omitting update", "insert", "persist", "delete table").forEach { operation ->
      listOf(false, true).forEach { commit ->
        val failure = IllegalStateException("SQL failed")
        val fixture = MutationFailureFixture(nativeFailure = failure)
        val transaction = fixture.connection.newTransaction()
        try {
          val actual = assertThrows(Exception::class.java) {
            fixture.execute(operation)
          }
          assertThat(actual === failure || actual.cause === failure)
            .isTrue()
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
  fun `native bulk failure rolls back without publishing`() {
    val fixture = MutationFailureFixture(nativeFailure = IllegalStateException("SQL failed"))
    fixture.adapter
      .bulkInsert(listOf(fixture.entity))
      .usingConnection(fixture.connection)
      .execute()
    assertThat(fixture.triggers)
      .isEmpty()
    assertThat(fixture.recording.rolledBackTransactions)
      .isEqualTo(1)
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
          recordChanges()
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
          recordChanges()
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
          recordChanges()
          throw failure
        }
      )
    }
    assertThat(actual)
      .isSameInstanceAs(failure)
    assertThat(fixture.triggers)
      .containsExactly(setOf("books", "authors", "publishers"))
  }

  private fun mutationContext(fixture: MutationFailureFixture) = OperationContext(
    adapter = fixture.adapter,
    configuration = OperationConfigurationSnapshot(connection = fixture.connection)
  )

  @Test
  fun `bulk callback failure rolls back without publishing`() {
    val fixture = MutationFailureFixture(idFailure = IllegalStateException("decoder failed"))
    fixture.adapter
      .bulkInsert(listOf(fixture.entity))
      .usingConnection(fixture.connection)
      .execute()
    assertThat(fixture.triggers)
      .isEmpty()
    assertThat(fixture.recording.rolledBackTransactions)
      .isEqualTo(1)
  }
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
  var closedStatements = 0
    private set
  var nativeExecutions = 0
    private set
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
          nativeExecutions++
          nativeFailure?.let { throw it }
          return when {
            zeroResult -> -1L
            else -> 1L
          }
        }

        override fun executeUpdateDelete(): Int {
          nativeExecutions++
          nativeFailure?.let { throw it }
          return when {
            zeroResult || sql.startsWith("UPDATE") && idFailure != null -> 0
            else -> 1
          }
        }

        override fun close() {
          closedStatements++
          closeFailure?.let { throw it }
          statement.close()
        }
      }
    }
  }
  private val helper = object : androidx.sqlite.db.SupportSQLiteOpenHelper by RecordingOpenHelper(recording) {
    override val writableDatabase = database
    override val readableDatabase = database
  }
  val connection = DbConnectionImpl(
    database = TestGeneratedDatabase(tableCount = 1),
    dbHelper = helper,
    queryScheduler = Schedulers.trampoline()
  )
  val triggers = mutableListOf<Set<String>>()
  val entity = TestEntity(
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

  init {
    connection.triggers.subscribe(::recordTriggers)
  }

  private fun recordTriggers(tables: Set<String>) {
    triggers.add(tables.toSet())
  }

  fun execute(operation: String): Any = when (operation) {
    "update" -> adapter
      .update(entity)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .usingConnection(connection)
      .execute()
    "null omitting update" -> adapter
      .persist(entity)
      .ignoreNullValues()
      .usingConnection(connection)
      .execute()
    "insert" -> adapter
      .insert(entity)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .usingConnection(connection)
      .execute()
    "persist" -> adapter
      .persist(entity)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .usingConnection(connection)
      .execute()
    "delete table" -> adapter
      .deleteTable()
      .usingConnection(connection)
      .execute()
    else -> error(operation)
  }
}
