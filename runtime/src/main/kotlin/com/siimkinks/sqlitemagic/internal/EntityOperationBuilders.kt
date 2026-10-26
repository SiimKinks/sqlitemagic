package com.siimkinks.sqlitemagic.internal

import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.siimkinks.sqlitemagic.BulkOperationOutcome
import com.siimkinks.sqlitemagic.BulkOperationOutcome.APPLIED
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityOperationByColumnBuilder
import com.siimkinks.sqlitemagic.executeBulkRxOperation
import com.siimkinks.sqlitemagic.internal.LogUtil.loggingFailure
import io.reactivex.Single

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

  internal fun <T> observeSingle(operation: (OperationContext) -> T) = configurationSnapshot().let { configuration ->
    Single.fromCallable {
      perform(
        operation = operation,
        configuration = configuration
      )
    }
  }

  internal fun executeBulk(operation: (OperationContext, () -> Boolean) -> BulkOperationOutcome) =
    perform(operation = { operation(it, ::neverCancelled) }) == APPLIED

  internal fun observeBulk(operation: (OperationContext, () -> Boolean) -> BulkOperationOutcome) =
    configurationSnapshot().let { configuration ->
      executeBulkRxOperation(
        contextFactory = { newContext(configuration) },
        operation = { context, isCancelled ->
          loggingFailure(message = OPERATION_FAILED) { operation(context, isCancelled) }
        }
      )
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
