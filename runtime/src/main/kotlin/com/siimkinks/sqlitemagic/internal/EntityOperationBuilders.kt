package com.siimkinks.sqlitemagic.internal

import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.siimkinks.sqlitemagic.BulkOperationOutcome
import com.siimkinks.sqlitemagic.BulkOperationOutcome.APPLIED
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.deferredBulk
import com.siimkinks.sqlitemagic.entity.EntityOperationByColumnBuilder
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.LogUtil.loggingFailure
import java.util.concurrent.CancellationException

private const val OPERATION_FAILED = "Operation failed"

@InternalSqliteMagicApi
abstract class OperationBuilder<R>(
  private val adapter: EntityAdapter<*>
) {
  private var connection: DbConnectionImpl? = null
  private var conflictAlgorithm = CONFLICT_NONE
  private var ignoreNullValues = false

  fun usingConnection(connection: DbConnection): R = self().also { this.connection = connection as DbConnectionImpl }

  fun conflictAlgorithm(conflictAlgorithm: Int): R = self().also { this.conflictAlgorithm = conflictAlgorithm }

  fun ignoreNullValues(): R = self().also { ignoreNullValues = true }

  internal fun configurationSnapshot() = OperationConfigurationSnapshot(
    connection = connection,
    conflictAlgorithm = conflictAlgorithm,
    ignoreNullValues = ignoreNullValues
  )

  internal fun newContext(configuration: OperationConfigurationSnapshot) = OperationContext(
    adapter = adapter,
    configuration = configuration
  )

  internal fun <T> perform(
    operation: (OperationContext) -> T,
    configuration: OperationConfigurationSnapshot = configurationSnapshot()
  ) = newContext(configuration).let { context ->
    loggingFailure(message = OPERATION_FAILED) { operation(context) }
  }

  internal fun <T> defer(operation: (OperationContext) -> T) = configurationSnapshot().let { configuration ->
    DeferredOperation(connection = configuration.connection) {
      perform(
        operation = operation,
        configuration = configuration
      )
    }
  }

  internal fun executeBulk(operation: (OperationContext, () -> Boolean) -> BulkOperationOutcome) =
    perform(operation = { operation(it, ::neverCancelled) }) == APPLIED

  internal fun deferBulk(operation: (OperationContext, () -> Boolean) -> BulkOperationOutcome) =
    configurationSnapshot().let { configuration ->
      deferredBulk(connection = configuration.connection) { isCancelled ->
        perform(
          operation = { operation(it, isCancelled) },
          configuration = configuration
        )
      }
    }

  @Suppress("UNCHECKED_CAST")
  internal fun self(): R = this as R
}

@InternalSqliteMagicApi
abstract class DefaultIdentityBuilder<R, M>(
  private val adapter: EntityDefaultIdentityAdapter<M>
) : OperationBuilder<R>(adapter), EntityOperationByColumnBuilder<R> {
  private var configuredColumnName: String? = null

  override fun byColumn(column: Unique<*>): R =
    self().also { configuredColumnName = adapter.identityColumnName(column) }

  internal fun identityColumnName() = configuredColumnName ?: adapter.defaultIdentityColumnName
}

private fun neverCancelled() = false

/**
 * Entity operation configured by a builder for a reactive adapter. It runs when [execute] is called, with the builder
 * configuration from the time it was created.
 *
 * @param T Result type
 */
@InternalSqliteMagicApi
open class DeferredOperation<T> internal constructor(
  internal val connection: DbConnectionImpl?,
  private val operation: (isCancelled: () -> Boolean) -> T
) {
  /**
   * Run the operation.
   *
   * @param isCancelled Whether the result is no longer needed. Bulk inserts, updates and persists check it before each
   *     entity and after the last one and throw [CancellationException] once it is `true`; other operations ignore it
   * @return What the `execute` method of the builder returns
   */
  fun execute(isCancelled: () -> Boolean) = operation(isCancelled)
}

/**
 * Entity operation whose reactive terminal only completes or fails: an update, or a bulk insert, update or persist.
 */
@InternalSqliteMagicApi
class DeferredCompletion internal constructor(
  connection: DbConnectionImpl?,
  operation: (isCancelled: () -> Boolean) -> Boolean,
  private val completion: (isCancelled: () -> Boolean) -> Exception?
) : DeferredOperation<Boolean>(
  connection = connection,
  operation = operation
) {
  /**
   * Run the operation for a terminal that only completes or fails.
   *
   * Failures other than the returned ones, such as SQLite errors, are thrown.
   *
   * @param isCancelled Whether the result is no longer needed, as in [execute]
   * @return `null` if the operation completed; otherwise the failure to report: [OperationFailedException] when an
   *     update changed no row without ignoring conflicts or a bulk operation failed, and [CancellationException] when
   *     [isCancelled] stopped a bulk operation
   */
  fun complete(isCancelled: () -> Boolean) = completion(isCancelled)
}
