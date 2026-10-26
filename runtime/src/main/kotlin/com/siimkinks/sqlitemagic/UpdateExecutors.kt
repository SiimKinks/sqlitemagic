package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.siimkinks.sqlitemagic.BulkEntityOutcome.APPLIED
import com.siimkinks.sqlitemagic.BulkEntityOutcome.FAILED
import com.siimkinks.sqlitemagic.BulkEntityOutcome.IGNORED
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.recursiveOrNull

internal object UpdateExecutors {
  fun <M> executeIgnoringNullValues(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String,
    standalone: Boolean = true
  ): Boolean {
    val updated = context.executeMutation(
      adapter = adapter,
      operation = {
        if (standalone) {
          context.logUpdate(entity = entity)
        }
        adapter.bindNotNullForUpdate(
          entity = entity,
          values = context.bindValues,
          columnName = columnName
        )
        val identity = adapter.identity(
          entity = entity,
          columnName = columnName
        )
        val updated = context
          .compileUpdateStatement(
            tableName = adapter.tableName,
            resolutionColumn = columnName,
            resolutionValue = identity
          )
          .use(::executeUpdateDelete)
        if (standalone) {
          logUpdateRowsAffected(rowsAffected = updated)
        }
        updated
      }
    )
    val successful = updated > 0
    if (standalone && successful) {
      context.sendTableTriggers(adapter)
    }
    return successful
  }

  fun <M> executeEntity(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String,
    standalone: Boolean = true
  ): Boolean {
    val rowsAffected = context.executeMutation(
      adapter = adapter,
      operation = {
        if (standalone) {
          context.logUpdate(entity = entity)
        }
        val rowsAffected = context.withUpdateStatement(
          sql = adapter.updateStatementSql(columnName),
          default = adapter is EntityDefaultIdentityAdapter<*> && columnName == adapter.defaultIdentityColumnName
        ) { statement ->
          adapter.bindToUpdateStatement(
            statement = statement,
            entity = entity,
            columnName = columnName
          )
          executeUpdateDelete(statement)
        }
        if (standalone) {
          logUpdateRowsAffected(rowsAffected = rowsAffected)
        }
        rowsAffected
      }
    )
    val updated = rowsAffected > 0
    if (standalone && updated) {
      context.sendTableTriggers(adapter)
    }
    return updated
  }

  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ): Boolean = when (val recursiveAdapter = adapter.recursiveOrNull()) {
    null -> updateEntity(
      adapter = adapter,
      entity = entity,
      context = context,
      columnName = columnName
    )
    else -> context.executeInTransaction {
      updateRecursively(
        adapter = adapter,
        recursiveAdapter = recursiveAdapter,
        entity = entity,
        context = context,
        columnName = columnName
      )
    }
  }

  private fun <M> updateRecursively(
    adapter: EntityIdentityAdapter<M>,
    recursiveAdapter: EntityRecursiveAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ) = updateEntity(
    adapter = adapter,
    entity = entity,
    context = context.childWithoutTableTriggers(),
    columnName = columnName
  ) && recursiveAdapter
    .updateRelationships(
      entity = entity,
      operations = RelationshipOperations(context)
    )
    .also { relationshipsUpdated ->
      if (relationshipsUpdated || context.conflictAlgorithm == CONFLICT_IGNORE) {
        context.sendTableTriggers(adapter)
      }
    }

  private fun <M> updateEntity(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ) = when {
    context.ignoreNullValues -> executeIgnoringNullValues(
      adapter = adapter,
      entity = entity,
      context = context,
      columnName = columnName
    )
    else -> executeEntity(
      adapter = adapter,
      entity = entity,
      context = context,
      columnName = columnName
    )
  }

  fun <M> executeBulk(
    adapter: EntityIdentityAdapter<M>,
    entities: Iterable<M>,
    columnName: String,
    context: OperationContext,
    isCancelled: () -> Boolean
  ) = executeBulkOperation(
    adapter = adapter,
    entities = entities,
    context = context,
    isCancelled = isCancelled,
    operation = { entity ->
      val entityUpdated = execute(
        adapter = adapter,
        entity = entity,
        context = context.childWithoutTableTriggers(),
        columnName = columnName
      )
      when {
        entityUpdated -> APPLIED
        context.conflictAlgorithm == CONFLICT_IGNORE -> IGNORED
        else -> FAILED
      }
    }
  )
}
