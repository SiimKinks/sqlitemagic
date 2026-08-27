package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.IdentityColumn

internal object UpdateExecutors {
  fun <M> executeIgnoringNullValues(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    byColumn: IdentityColumn<M>,
    publishTriggers: Boolean = true,
    logOperation: Boolean = true
  ): Boolean {
    if (logOperation) {
      context.logUpdate(entity = entity)
    }
    adapter.bindNotNullForUpdate(
      entity = entity,
      values = context.bindValues,
      byColumn = byColumn
    )
    val identity = adapter.identity(
      entity = entity,
      byColumn = byColumn
    )
    val identityColumn = identity.columnName
    val updated = when {
      context.bindValues.isEmpty -> context
        .entityDbManager()
        .compileStatement(
          "UPDATE${context.conflictValue()} ${adapter.tableName} SET $identityColumn=$identityColumn WHERE $identityColumn=?"
        )
        .use { statement ->
          statement.bindString(1, identity.serializedValue)
          statement.executeUpdateDelete()
        }
      else -> context
        .variableArgsOperationHelper
        .compileStatement(
          OperationHelper.Op.UPDATE,
          adapter.tableName,
          adapter.maxColumns,
          context.bindValues,
          identityColumn,
          identity.serializedValue,
          context.entityDbManager()
        )
        .use(SupportSQLiteStatement::executeUpdateDelete)
    }
    val successful = updated > 0
    if (publishTriggers && successful) {
      context.sendTableTriggers(adapter)
    }
    if (logOperation) {
      context.logUpdateRowsAffected(rowsAffected = updated)
    }
    return successful
  }

  fun <M> executeEntity(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    byColumn: IdentityColumn<M>,
    defaultIdentity: Boolean,
    publishTriggers: Boolean = true,
    logOperation: Boolean = true
  ): Boolean {
    if (logOperation) {
      context.logUpdate(entity = entity)
    }
    val operationByColumns = when {
      defaultIdentity -> null
      else -> arrayListOf<Column<*, *, *, *, *>>(byColumn)
    }
    val rowsAffected = context
      .operationHelper(
        operation = OperationHelper.Op.UPDATE,
        operationByColumns = operationByColumns
      )
      .use { helper ->
        val statement = helper.getUpdateStatement(
          adapter.tableName,
          adapter.updateStatementSql(byColumn),
          context.entityDbManager()
        )
        synchronized(statement) {
          adapter.bindToUpdateStatement(
            statement = statement,
            entity = entity,
            byColumn = byColumn
          )
          statement.executeUpdateDelete()
        }
      }
    val updated = rowsAffected > 0
    if (publishTriggers && updated) {
      context.sendTableTriggers(adapter)
    }
    if (logOperation) {
      context.logUpdateRowsAffected(rowsAffected = rowsAffected)
    }
    return updated
  }

  @Suppress("UNCHECKED_CAST")
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    byColumn: IdentityColumn<M>,
    defaultIdentity: Boolean
  ): Boolean {
    val recursiveAdapter = adapter as? EntityRecursiveAdapter<M>
    val isRecursive = recursiveAdapter != null
    val operation = {
      val operationContext = when {
        isRecursive -> context.childWithoutTableTriggers()
        else -> context
      }
      val parentUpdated = when {
        context.ignoreNullValues -> executeIgnoringNullValues(
          adapter = adapter,
          entity = entity,
          context = operationContext,
          byColumn = byColumn,
          publishTriggers = !isRecursive
        )
        else -> executeEntity(
          adapter = adapter,
          entity = entity,
          context = operationContext,
          byColumn = byColumn,
          defaultIdentity = defaultIdentity
        )
      }
      when {
        !isRecursive -> parentUpdated
        !parentUpdated -> false
        else -> recursiveAdapter
          .updateRelationships(
            entity = entity,
            operations = RelationshipOperations(context)
          )
          .also { relationshipsUpdated ->
            if (relationshipsUpdated || context.conflictAlgorithm == CONFLICT_IGNORE) {
              context.sendTableTriggers(adapter)
            }
          }
      }
    }
    return try {
      when {
        isRecursive -> context.executeInTransaction(operation)
        else -> operation()
      }
    } catch (exception: OperationFailedException) {
      context.logError(exception = exception, message = "Operation failed")
      throw exception
    }
  }

  fun <M> executeBulk(
    adapter: EntityIdentityAdapter<M>,
    entities: Iterable<M>,
    byColumn: IdentityColumn<M>,
    defaultIdentity: Boolean,
    context: OperationContext,
    isCancelled: () -> Boolean
  ): BulkOperationOutcome = executeBulkOperation(
    adapter = adapter,
    entities = entities,
    context = context,
    isCancelled = isCancelled,
    operation = { entity ->
      val entityUpdated = execute(
        adapter = adapter,
        entity = entity,
        context = context.childWithoutTableTriggers(),
        byColumn = byColumn,
        defaultIdentity = defaultIdentity
      )
      when {
        entityUpdated -> BulkEntityOutcome.APPLIED
        context.conflictAlgorithm == CONFLICT_IGNORE -> BulkEntityOutcome.IGNORED
        else -> BulkEntityOutcome.FAILED
      }
    }
  )
}

private fun OperationContext.conflictValue() =
  ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm]
