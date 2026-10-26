package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.siimkinks.sqlitemagic.BulkEntityOutcome.APPLIED
import com.siimkinks.sqlitemagic.BulkEntityOutcome.IGNORED
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.recursiveOrNull

internal object PersistExecutors {
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    columnName: String
  ): EntityPersistResult = when (val recursiveAdapter = adapter.recursiveOrNull()) {
    null -> persist(
      adapter = adapter,
      recursiveAdapter = null,
      entity = entity,
      context = context,
      columnName = columnName
    )
    else -> context.executeInTransaction {
      persist(
        adapter = adapter,
        recursiveAdapter = recursiveAdapter,
        entity = entity,
        context = context,
        columnName = columnName
      )
    }
  }

  private fun <M> persist(
    adapter: EntityIdentityAdapter<M>,
    recursiveAdapter: EntityRecursiveAdapter<M>?,
    entity: M,
    context: OperationContext,
    columnName: String
  ): EntityPersistResult {
    val relationshipsPersisted = recursiveAdapter
      ?.persistRelationships(
        entity = entity,
        operations = RelationshipOperations(context)
      )
      ?: true
    return when {
      !relationshipsPersisted -> {
        if (context.conflictAlgorithm != CONFLICT_IGNORE) {
          throw OperationFailedException("Failed to persist relationship for $entity")
        }
        context.sendTableTriggers(adapter)
        EntityPersistResult.Ignored
      }
      else -> {
        context.logPersist(entity = entity)
        val updated = when {
          !adapter.hasIdentityValue(
            entity = entity,
            columnName = columnName
          ) -> false
          context.ignoreNullValues -> UpdateExecutors.executeIgnoringNullValues(
            adapter = adapter,
            entity = entity,
            context = context,
            columnName = columnName,
            standalone = false
          )
          else -> UpdateExecutors.executeEntity(
            adapter = adapter,
            entity = entity,
            context = context,
            columnName = columnName,
            standalone = false
          )
        }
        when {
          updated -> {
            context.sendTableTriggers(adapter)
            EntityPersistResult.Updated
          }
          else -> {
            logPersistUpdateFailed()
            val insertResult = when {
              context.ignoreNullValues -> InsertExecutors.mapResult(
                adapter = adapter,
                rowId = InsertExecutors.executeIgnoringNullValues(
                  adapter = adapter,
                  entity = entity,
                  context = context
                ),
                conflictAlgorithm = context.conflictAlgorithm,
                failureMessage = "Failed to persist $entity"
              )
              else -> InsertExecutors.execute(
                adapter = adapter,
                entity = entity,
                context = context,
                standalone = false
              )
            }
            when (insertResult) {
              EntityInsertResult.Ignored -> {
                if (recursiveAdapter != null) {
                  context.sendTableTriggers(adapter)
                }
                EntityPersistResult.Ignored
              }
              is EntityInsertResult.Inserted -> {
                context.sendTableTriggers(adapter)
                logPersistInsertId(rowId = insertResult.rowId)
                EntityPersistResult.Inserted(insertResult.rowId)
              }
            }
          }
        }
      }
    }
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
      val result = execute(
        adapter = adapter,
        entity = entity,
        context = context.childWithoutTableTriggers(),
        columnName = columnName
      )
      when (result) {
        EntityPersistResult.Ignored -> IGNORED
        else -> APPLIED
      }
    }
  )
}
