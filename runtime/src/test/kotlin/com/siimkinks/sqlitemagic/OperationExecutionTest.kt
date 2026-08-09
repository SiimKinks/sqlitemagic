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
      operation = { _, _ -> true }
    )
      .test()
      .assertResult()
  }

  @Test
  fun `unsuccessful bulk operation emits operation failure`() {
    executeBulkRxOperation(
      contextFactory = ::context,
      operation = { _, _ -> false }
    )
      .test()
      .assertFailure(OperationFailedException::class.java)
  }

  @Test
  fun `unsuccessful ignored bulk operation completes`() {
    executeBulkRxOperation(
      contextFactory = { context(conflictAlgorithm = CONFLICT_IGNORE) },
      operation = { _, _ -> false }
    )
      .test()
      .assertResult()
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

  private fun context(conflictAlgorithm: Int = CONFLICT_NONE) = OperationContext(
    adapter = metadata,
    configuration = OperationConfigurationSnapshot(
      connection = newConnection().connection,
      conflictAlgorithm = conflictAlgorithm
    )
  )

  private val metadata = object : EntityAdapterMetadata {
    override val tableName = "books"
    override val insertSql = "INSERT%s INTO books"
    override val tablePosition = 0
    override val withoutRowId = false
    override val maxColumns = 1
  }
}
