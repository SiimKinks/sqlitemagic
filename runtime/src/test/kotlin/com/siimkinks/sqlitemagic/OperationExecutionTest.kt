package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CancellationException

internal class OperationExecutionTest {
  @Test
  fun `deferred bulk outcomes execute as applied only and complete unless the operation failed`() {
    val results = BulkOperationOutcome.entries.associateWith { outcome ->
      val operation = deferredBulk { outcome }
      operation.execute(isCancelled = { false }) to operation.complete(isCancelled = { false })?.toString()
    }

    assertThat(results).isEqualTo(
      mapOf(
        BulkOperationOutcome.APPLIED to (true to null),
        BulkOperationOutcome.EMPTY to (false to null),
        BulkOperationOutcome.IGNORED to (false to null),
        BulkOperationOutcome.FAILED to (false to "${OperationFailedException::class.java.name}: Bulk operation failed")
      )
    )
  }

  @Test
  fun `bulk operation checks cancellation after the final entity`() {
    val connection = newConnection()
    var cancelled = false
    val entities = oneShotIterable(
      Iterable {
        iterator {
          yield(TestEntity())
          cancelled = true
        }
      }
    )
    assertThrows(CancellationException::class.java) {
      executeBulkOperation(
        adapter = TestAdapter(),
        entities = entities,
        context = contextFor(connection = connection.connection),
        isCancelled = { cancelled },
        operation = { BulkEntityOutcome.APPLIED }
      )
    }

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
    val actual = assertThrows(OperationFailedException::class.java) {
      executeBulkOperation(
        adapter = TestAdapter(),
        entities = listOf(TestEntity()),
        context = contextFor(
          connection = connection.connection,
          conflictAlgorithm = CONFLICT_IGNORE
        ),
        isCancelled = { false },
        operation = { throw expected }
      )
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

  private fun contextFor(
    connection: DbConnectionImpl,
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
