package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.IdentityColumn

internal object PersistExecutors {
  @Suppress("UNCHECKED_CAST")
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    byColumn: IdentityColumn<M>,
    defaultIdentity: Boolean
  ): EntityPersistResult {
    val recursiveAdapter = adapter as? EntityRecursiveAdapter<M>
    val operation = {
      val relationshipsPersisted = recursiveAdapter
        ?.persistRelationships(
          entity = entity,
          operations = RelationshipOperations(context)
        )
        ?: true
      if (relationshipsPersisted) {
        context.logPersist(entity = entity)
      }
      when {
        !relationshipsPersisted -> {
          if (context.conflictAlgorithm != CONFLICT_IGNORE) {
            throw OperationFailedException("Failed to persist relationship for $entity")
          }
          context.sendTableTriggers(adapter)
          EntityPersistResult.Ignored
        }
        else -> {
          val updated = when {
            !adapter.hasIdentityValue(entity = entity, byColumn = byColumn) -> false
            context.ignoreNullValues -> UpdateExecutors.executeIgnoringNullValues(
              adapter = adapter,
              entity = entity,
              context = context,
              byColumn = byColumn,
              publishTriggers = false,
              logOperation = false
            )
            else -> UpdateExecutors.executeEntity(
              adapter = adapter,
              entity = entity,
              context = context,
              byColumn = byColumn,
              defaultIdentity = defaultIdentity,
              publishTriggers = false,
              logOperation = false
            )
          }
          if (updated) {
            context.sendTableTriggers(adapter)
            EntityPersistResult.Updated
          } else {
            context.logPersistUpdateFailed()
            val insertResult = when {
              context.ignoreNullValues -> InsertExecutors.mapResult(
                adapter = adapter,
                rowId = InsertExecutors.executeIgnoringNullValues(
                  adapter = adapter,
                  entity = entity,
                  context = context,
                  logOperation = false
                ),
                conflictAlgorithm = context.conflictAlgorithm,
                failureMessage = "Failed to persist $entity"
              )
              else -> InsertExecutors.execute(
                adapter = adapter,
                entity = entity,
                context = context,
                includeRelationships = false,
                publishTriggers = false,
                logOperation = false
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
                context.logPersistInsertId(rowId = insertResult.rowId)
                context.sendTableTriggers(adapter)
                EntityPersistResult.Inserted(insertResult.rowId)
              }
            }
          }
        }
      }
    }
    return try {
      when {
        recursiveAdapter != null -> context.executeInTransaction(operation)
        else -> operation()
      }
    } catch (exception: OperationFailedException) {
      context.logError(exception = exception, message = "Operation failed")
      throw exception
    } catch (exception: Exception) {
      throw OperationFailedException("Failed to persist $entity", exception)
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
      val result = execute(
        adapter = adapter,
        entity = entity,
        context = context.childWithoutTableTriggers(),
        byColumn = byColumn,
        defaultIdentity = defaultIdentity
      )
      when (result) {
        EntityPersistResult.Ignored -> BulkEntityOutcome.IGNORED
        else -> BulkEntityOutcome.APPLIED
      }
    }
  )
}
