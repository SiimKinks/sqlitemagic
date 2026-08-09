package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

internal interface OperationConfiguration {
  val connection: DbConnection
  val conflictAlgorithm: Int
  val ignoreNullValues: Boolean
}

internal data class OperationConfigurationSnapshot(
  override val connection: DbConnection,
  override val conflictAlgorithm: Int = CONFLICT_NONE,
  override val ignoreNullValues: Boolean = false
) : OperationConfiguration

internal class OperationContext private constructor(
  override val tableName: String,
  val tablePosition: Int,
  val configuration: OperationConfigurationSnapshot,
  private val sharedState: SharedState,
  val skipTableTriggers: Boolean
) : OperationConfiguration by configuration, Loggable {
  constructor(
    adapter: EntityAdapterMetadata,
    configuration: OperationConfigurationSnapshot
  ) : this(
    tableName = adapter.tableName,
    tablePosition = adapter.tablePosition,
    configuration = configuration,
    sharedState = SharedState(
      conflictAlgorithm = configuration.conflictAlgorithm
    ),
    skipTableTriggers = false
  )

  val bindValues get() = sharedState.bindValues
  val variableArgsOperationHelper get() = sharedState.variableArgsOperationHelper
  internal val generatedRelationshipIds: MutableMap<String, Long> = linkedMapOf()

  fun child(
    skipTableTriggers: Boolean = this.skipTableTriggers,
    ignoreNullValues: Boolean = this.ignoreNullValues
  ) = OperationContext(
    tableName = tableName,
    tablePosition = tablePosition,
    configuration = configuration.copy(ignoreNullValues = ignoreNullValues),
    sharedState = sharedState,
    skipTableTriggers = skipTableTriggers
  )

  fun childFor(
    adapter: EntityAdapterMetadata,
    skipTableTriggers: Boolean = this.skipTableTriggers,
    ignoreNullValues: Boolean = this.ignoreNullValues
  ) = OperationContext(
    tableName = adapter.tableName,
    tablePosition = adapter.tablePosition,
    configuration = configuration.copy(ignoreNullValues = ignoreNullValues),
    sharedState = sharedState,
    skipTableTriggers = skipTableTriggers
  )

  fun childWithoutTableTriggers() = child(skipTableTriggers = true)

  fun operationHelper(
    operation: Int,
    operationByColumns: ArrayList<Column<*, *, *, *, *>>?
  ) = OperationHelper(
    conflictAlgorithm,
    operation,
    operationByColumns
  )

  fun entityDbManager() = (connection as DbConnectionImpl).getEntityDbManager(null, tablePosition)

  fun sendTableTriggers(adapter: EntityAdapter<*>) {
    if (!skipTableTriggers) {
      val tableNames = when (adapter) {
        is EntityRecursiveAdapter<*> -> adapter.triggerTableNames
        else -> arrayOf(adapter.tableName)
      }
      (connection as DbConnectionImpl).sendTableTriggers(*tableNames)
    }
  }

  fun <T> executeInTransaction(operation: () -> T): T {
    if (sharedState.transactionDepth > 0) {
      return operation()
    }
    sharedState.transactionDepth++
    return try {
      val transaction = connection.newTransaction()
      try {
        val result = operation()
        if (isSuccessful(result)) {
          transaction.markSuccessful()
        }
        result
      } finally {
        transaction.end()
      }
    } catch (exception: Exception) {
      logError(
        exception = exception,
        message = "Error while executing db transaction"
      )
      throw exception
    } finally {
      sharedState.transactionDepth--
    }
  }

  private fun <T> isSuccessful(result: T) = when (result) {
    is Boolean -> result
    is Long -> result != -1L
    EntityInsertResult.Ignored -> false
    EntityPersistResult.Ignored -> false
    else -> true
  }

  private class SharedState(
    conflictAlgorithm: Int
  ) {
    val bindValues = SimpleArrayMap<String, Any>()
    val variableArgsOperationHelper by lazy(LazyThreadSafetyMode.NONE) {
      VariableArgsOperationHelper(conflictAlgorithm)
    }
    var transactionDepth = 0
  }
}
