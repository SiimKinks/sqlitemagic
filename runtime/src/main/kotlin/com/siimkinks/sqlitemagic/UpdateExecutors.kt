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
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ) = when (val recursiveAdapter = adapter.recursiveOrNull()) {
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

  fun <M> updateEntity(
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
        val rowsAffected = when {
          context.ignoreNullValues -> updateNotNullValues(
            adapter = adapter,
            entity = entity,
            context = context,
            columnName = columnName
          )
          else -> updateAllValues(
            adapter = adapter,
            entity = entity,
            context = context,
            columnName = columnName
          )
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

  private fun <M> EntityMutationExecution.updateNotNullValues(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ): Int {
    adapter.bindNotNullForUpdate(
      entity = entity,
      values = context.bindValues,
      columnName = columnName,
      generatedRelationshipIds = context.generatedRelationshipIds
    )
    return context.withBoundUpdateStatement(
      tableName = adapter.tableName,
      resolutionColumn = columnName,
      resolutionValue = adapter.identity(
        entity = entity,
        columnName = columnName
      ),
      block = this::executeUpdateDelete
    )
  }

  private fun <M> EntityMutationExecution.updateAllValues(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ) = context.withUpdateStatement(
    template = adapter.updateStatementSql(columnName),
    default = adapter is EntityDefaultIdentityAdapter<*> && columnName == adapter.defaultIdentityColumnName
  ) { statement, results ->
    adapter.bindToUpdateStatement(
      statement = statement,
      entity = entity,
      columnName = columnName,
      generatedRelationshipIds = context.generatedRelationshipIds
    )
    executeUpdateDelete(
      statement = statement,
      results = results
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
