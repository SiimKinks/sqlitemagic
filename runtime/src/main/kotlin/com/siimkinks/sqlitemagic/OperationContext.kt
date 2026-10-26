package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

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
  val generatedRelationshipIds get() = rememberedRelationshipIds ?: emptyMap()

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

  val customConflict get() = conflictAlgorithm != CONFLICT_NONE && conflictAlgorithm != CONFLICT_ABORT

  inline fun <T> withInsertStatement(sql: String, block: (SupportSQLiteStatement) -> T): T = withStatement(
    sql = sql,
    cached = !customConflict,
    cachedStatement = { entityDbManager().insertStatement(sql) },
    block = block
  )

  inline fun <T> withUpdateStatement(
    sql: String,
    default: Boolean,
    block: (SupportSQLiteStatement) -> T
  ): T = withStatement(
    sql = sql,
    cached = default && !customConflict,
    cachedStatement = { entityDbManager().updateStatement(sql) },
    block = block
  )

  inline fun <T> withStatement(
    sql: String,
    cached: Boolean,
    cachedStatement: () -> SupportSQLiteStatement,
    block: (SupportSQLiteStatement) -> T
  ): T = when {
    cached -> cachedStatement().let { statement ->
      synchronized(statement) {
        block(statement)
      }
    }
    inTransaction -> block(transactionStatement(sql))
    else -> compileStatement(sql).let { statement ->
      try {
        block(statement)
      } finally {
        statement.closeQuietly()
      }
    }
  }

  val inTransaction get() = sharedState.transactionDepth > 0

  fun transactionStatement(sql: String) = sharedState.statements[sql]
    ?: compileStatement(sql).also { sharedState.statements[sql] = it }

  fun compileStatement(sql: String) = entityDbManager().compileStatement(
    sql = sql,
    conflictAlgorithm = conflictAlgorithm
  )

  fun entityDbManager() = connection.getEntityDbManager(
    moduleName = adapter.moduleName,
    tablePos = adapter.tablePosition
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
  ): T = EntityMutationExecution().let { execution ->
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
        sharedState.closeStatements()
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
    val statements = MutableScatterMap<String, SupportSQLiteStatement>(initialCapacity = 0)
    var transactionDepth = 0

    fun closeStatements() {
      statements.forEachValue(SupportSQLiteStatement::closeQuietly)
      statements.clear()
    }
  }
}

internal class EntityMutationExecution {
  var possibleChanges = false
    private set

  fun executeInsert(statement: SupportSQLiteStatement): Long {
    possibleChanges = true
    return statement
      .executeInsert()
      .also { possibleChanges = it != -1L }
  }

  fun executeUpdateDelete(statement: SupportSQLiteStatement): Int {
    possibleChanges = true
    return statement
      .executeUpdateDelete()
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
