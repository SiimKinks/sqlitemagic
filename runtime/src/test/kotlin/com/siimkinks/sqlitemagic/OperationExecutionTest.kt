package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import io.reactivex.observers.TestObserver
import org.junit.Test
import java.util.concurrent.CancellationException

internal class OperationExecutionTest {
  @Test
  fun `successful bulk operation completes`() {
    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, _ -> BulkOperationOutcome.APPLIED }
    )
      .test()
      .assertResult()
  }

  @Test
  fun `unsuccessful bulk operation emits operation failure`() {
    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, _ -> BulkOperationOutcome.FAILED }
    )
      .test()
      .assertFailure(OperationFailedException::class.java)
  }

  @Test
  fun `failed bulk operation emits operation failure when conflicts are ignored`() {
    executeBulkRxOperation(
      contextFactory = { context(conflictAlgorithm = CONFLICT_IGNORE) },
      operation = { _, _ -> BulkOperationOutcome.FAILED }
    )
      .test()
      .assertFailure(OperationFailedException::class.java)
  }

  @Test
  fun `empty bulk operation completes`() {
    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, _ -> BulkOperationOutcome.EMPTY }
    )
      .test()
      .assertResult()
  }

  @Test
  fun `ignored bulk operation completes`() {
    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, _ -> BulkOperationOutcome.IGNORED }
    )
      .test()
      .assertResult()
  }

  @Test
  fun `bulk operation checks cancellation after the final entity`() {
    val connection = newConnection()
    val entity = TestEntity(
      id = "id-1",
      key = "key-1",
      name = "name"
    )
    var cancelled = false
    var iteratorRequested = false
    val entities = object : Iterable<TestEntity> {
      override fun iterator() = object : Iterator<TestEntity> {
        private var index = 0

        override fun hasNext(): Boolean {
          if (index == 1) {
            cancelled = true
          }
          return index == 0
        }

        override fun next() = entity.also { index++ }
      }.also {
        check(!iteratorRequested) { "Iterator requested more than once" }
        iteratorRequested = true
      }
    }
    val cancellation = try {
      executeBulkOperation(
        adapter = TestAdapter(),
        entities = entities,
        context = contextFor(connection = connection.connection),
        isCancelled = { cancelled },
        operation = { BulkEntityOutcome.APPLIED }
      )
      null
    } catch (exception: CancellationException) {
      exception
    }

    assertThat(cancellation).isNotNull()
    assertThat(connection.transactionResult(Unit))
      .isEqualTo(
        TransactionResult(
          result = Unit,
          committed = 0,
          rolledBack = 1
        )
      )
    assertThat(connection.triggers).isEmpty()
  }

  @Test
  fun `bulk operation rethrows entity failure when conflicts are ignored`() {
    val connection = newConnection()
    val expected = OperationFailedException("expected failure")
    val actual = try {
      executeBulkOperation(
        adapter = TestAdapter(),
        entities = listOf(
          TestEntity(
            id = "id-1",
            key = "key-1",
            name = "name"
          )
        ),
        context = contextFor(
          connection = connection.connection,
          conflictAlgorithm = CONFLICT_IGNORE
        ),
        isCancelled = { false },
        operation = { throw expected }
      )
      null
    } catch (exception: OperationFailedException) {
      exception
    }

    assertThat(actual).isSameInstanceAs(expected)
    assertThat(connection.transactionResult(Unit))
      .isEqualTo(
        TransactionResult(
          result = Unit,
          committed = 0,
          rolledBack = 1
        )
      )
    assertThat(connection.triggers).isEmpty()
  }

  @Test
  fun `cancellation exception without disposal is emitted`() {
    val cancellation = CancellationException("not caused by disposal")

    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, _ -> throw cancellation }
    )
      .test()
      .assertFailure(cancellation::equals)
  }

  @Test
  fun `cancellation exception after disposal is suppressed`() {
    val observer = TestObserver<Void>()

    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, isCancelled ->
        observer.dispose()
        assertThat(isCancelled()).isTrue()
        throw CancellationException()
      }
    ).subscribe(observer)

    observer.assertEmpty()
  }

  private fun context(conflictAlgorithm: Int = CONFLICT_NONE) = contextFor(
    connection = newConnection().connection,
    conflictAlgorithm = conflictAlgorithm
  )

  private fun contextFor(
    connection: DbConnection,
    conflictAlgorithm: Int = CONFLICT_NONE
  ) = OperationContext(
    adapter = metadata,
    configuration = OperationConfigurationSnapshot(
      connection = connection,
      conflictAlgorithm = conflictAlgorithm
    )
  )

  private val metadata = object : EntityAdapterMetadata {
    override val moduleName: String? = null
    override val tableName = "books"
    override val insertSql = "INSERT%s INTO books"
    override val tablePosition = 0
    override val withoutRowId = false
    override val maxColumns = 1
  }
}
