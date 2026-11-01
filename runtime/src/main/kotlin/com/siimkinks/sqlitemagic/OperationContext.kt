package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutationResults

internal data class OperationConfigurationSnapshot(
  val connection: DbConnectionImpl?,
  val conflictAlgorithm: Int = CONFLICT_NONE,
  val ignoreNullValues: Boolean = false
)

internal class OperationContext private constructor(
  val adapter: EntityAdapterMetadata,
  val configuration: OperationConfigurationSnapshot,
  val connection: DbConnectionImpl,
  private val sharedState: SharedState,
  val skipTableTriggers: Boolean
) {
  constructor(
    adapter: EntityAdapterMetadata,
    configuration: OperationConfigurationSnapshot
  ) : this(
    adapter = adapter,
    configuration = configuration,
    connection = configuration.connection.orDefault(),
    sharedState = SharedState(
      bindValuesCapacity = when {
        configuration.ignoreNullValues -> adapter.maxColumns
        else -> 0
      }
    ),
    skipTableTriggers = false
  )

  val conflictAlgorithm get() = configuration.conflictAlgorithm
  val ignoreNullValues get() = configuration.ignoreNullValues
  val bindValues get() = sharedState.bindValues
  private var rememberedRelationshipIds: MutableMap<String, Long>? = null
  val generatedRelationshipIds get() = rememberedRelationshipIds.orEmpty()

  fun rememberGeneratedId(columnName: String, rowId: Long) {
    val ids = rememberedRelationshipIds ?: HashMap<String, Long>().also { rememberedRelationshipIds = it }
    ids[columnName] = rowId
  }

  fun childWithoutTableTriggers() = OperationContext(
    adapter = adapter,
    configuration = configuration,
    connection = connection,
    sharedState = sharedState,
    skipTableTriggers = true
  )

  fun relationshipContext(
    adapter: EntityAdapterMetadata,
    ignoreNullValues: Boolean
  ) = OperationContext(
    adapter = adapter,
    configuration = when {
      ignoreNullValues == configuration.ignoreNullValues -> configuration
      else -> configuration.copy(ignoreNullValues = ignoreNullValues)
    },
    connection = connection,
    sharedState = sharedState,
    skipTableTriggers = true
  )

  val statementConflictAlgorithm
    get() = when (conflictAlgorithm) {
      CONFLICT_ABORT -> CONFLICT_NONE
      else -> conflictAlgorithm
    }

  inline fun <T> withInsertStatement(
    template: String,
    block: (SQLiteStatement, MutationResults) -> T
  ) = withStatement(
    sql = { insertSql(template) },
    block = block
  )

  inline fun <T> withUpdateStatement(
    template: String,
    default: Boolean,
    block: (SQLiteStatement, MutationResults) -> T
  ) = withStatement(
    sql = {
      updateSql(
        template = template,
        default = default
      )
    },
    block = block
  )

  fun insertSql(template: String) = entitySql().insert(
    template = template,
    conflictAlgorithm = statementConflictAlgorithm
  )

  fun updateSql(template: String, default: Boolean) = when {
    default -> entitySql().defaultUpdate(
      template = template,
      conflictAlgorithm = statementConflictAlgorithm
    )
    else -> entitySql().update(
      template = template,
      conflictAlgorithm = statementConflictAlgorithm
    )
  }

  fun deleteSql(columnName: String, count: Int) = when (count) {
    1 -> entitySql().deleteOne(
      tableName = adapter.tableName,
      columnName = columnName
    )
    else -> buildString {
      append("DELETE FROM ")
      append(adapter.tableName)
      append(" WHERE ")
      append(columnName)
      append(" IN (")
      appendPlaceholders(count)
      append(')')
    }
  }

  fun deleteTableSql() = entitySql().deleteTable(adapter.tableName)

  inline fun <T> withStatement(
    sql: () -> String,
    block: (SQLiteStatement, MutationResults) -> T
  ) = connection.write { pooled ->
    pooled.withStatement(sql()) { statement ->
      block(statement, pooled.mutations)
    }
  }

  private val inTransaction get() = sharedState.transactionDepth > 0

  fun entitySql() = connection.entitySql(
    moduleName = adapter.moduleName,
    tablePosition = adapter.tablePosition
  )

  fun sendTableTriggers(adapter: EntityAdapter<*>) {
    if (!skipTableTriggers) {
      publishTableTriggers(adapter)
    }
  }

  fun publishTableTriggers(adapter: EntityAdapter<*>) {
    when (adapter) {
      is EntityRecursiveAdapter<*> -> connection.sendTableTriggers(adapter.triggerTableNames)
      else -> connection.sendTableTrigger(adapter.tableName)
    }
  }

  inline fun <T> executeMutation(
    adapter: EntityAdapter<*>,
    operation: EntityMutationExecution.() -> T
  ) = EntityMutationExecution().let { execution ->
    notifyingOnFailure(
      notify = {
        if (execution.possibleChanges) {
          publishTableTriggers(adapter)
        }
      },
      block = { execution.operation() }
    )
  }

  fun <T> executeInTransaction(operation: () -> T): T {
    if (inTransaction) {
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
    } finally {
      sharedState.transactionDepth--
    }
  }

  private fun <T> isSuccessful(result: T) = when (result) {
    is Boolean -> result
    EntityInsertResult.Ignored -> false
    EntityPersistResult.Ignored -> false
    else -> true
  }

  private class SharedState(bindValuesCapacity: Int) {
    val bindValues = MutableScatterMap<String, Any>(initialCapacity = bindValuesCapacity)
    var transactionDepth = 0
  }
}

internal class EntityMutationExecution {
  var possibleChanges = false
    private set

  fun executeInsert(statement: SQLiteStatement, results: MutationResults): Long {
    possibleChanges = true
    return results
      .executeInsert(statement)
      .also { possibleChanges = it != -1L }
  }

  fun executeUpdateDelete(statement: SQLiteStatement, results: MutationResults): Int {
    possibleChanges = true
    return results
      .executeUpdateDelete(statement)
      .also { possibleChanges = it > 0 }
  }
}

internal fun OperationContext.logInsert(entity: Any?) = logDebug {
  "INSERT\n  table: ${adapter.tableName}\n  object: $entity"
}

internal fun logInsertId(rowId: Long) = logDebug { "INSERT id: $rowId" }

internal fun OperationContext.logUpdate(entity: Any?) = logDebug {
  "UPDATE\n  table: ${adapter.tableName}\n  object: $entity"
}

internal fun logUpdateRowsAffected(rowsAffected: Int) = logDebug { "UPDATE rows affected: $rowsAffected" }

internal fun OperationContext.logPersist(entity: Any?) = logDebug {
  "PERSIST\n  table: ${adapter.tableName}\n  object: $entity"
}

internal fun logPersistUpdateFailed() = logDebug { "PERSIST update failed; trying insertion" }

internal fun logPersistInsertId(rowId: Long?) = logDebug { "PERSIST insert id: $rowId" }
