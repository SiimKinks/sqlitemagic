package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import io.reactivex.Completable
import java.util.concurrent.CancellationException

internal class RelationshipOperations(
  private val context: OperationContext
) : EntityRelationshipOperations {
  override val ignoreNullValues: Boolean
    get() = context.ignoreNullValues

  override fun <M> insert(
    adapter: EntityDefaultIdentityAdapter<M>,
    entity: M
  ): EntityInsertResult = InsertExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = context.childFor(
      adapter = adapter,
      skipTableTriggers = true,
      ignoreNullValues = false
    )
  )

  override fun <M> update(
    adapter: EntityDefaultIdentityAdapter<M>,
    entity: M
  ): Boolean = UpdateExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = context.childFor(
      adapter = adapter,
      skipTableTriggers = true,
      ignoreNullValues = false
    ),
    byColumn = adapter.defaultIdentityColumn,
    defaultIdentity = true
  )

  override fun <M> persist(
    adapter: EntityDefaultIdentityAdapter<M>,
    entity: M
  ): EntityPersistResult = PersistExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = context.childFor(
      adapter = adapter,
      skipTableTriggers = true,
      ignoreNullValues = ignoreNullValues
    ),
    byColumn = adapter.defaultIdentityColumn,
    defaultIdentity = true
  )

  override fun rememberGeneratedId(columnName: String, rowId: Long) {
    context.generatedRelationshipIds[columnName] = rowId
  }
}

internal enum class BulkOperationOutcome {
  APPLIED,
  EMPTY,
  IGNORED,
  FAILED
}

internal enum class BulkEntityOutcome {
  APPLIED,
  IGNORED,
  FAILED
}

internal fun <M> executeBulkOperation(
  adapter: EntityAdapter<M>,
  entities: Iterable<M>,
  context: OperationContext,
  isCancelled: () -> Boolean,
  operation: (M) -> BulkEntityOutcome
): BulkOperationOutcome {
  var outcome = BulkOperationOutcome.EMPTY

  fun executeEntities(): Boolean {
    for (entity in entities) {
      if (outcome == BulkOperationOutcome.EMPTY) {
        outcome = BulkOperationOutcome.IGNORED
      }
      if (isCancelled()) {
        throw CancellationException()
      }
      val entityOutcome = try {
        operation(entity)
      } catch (exception: OperationFailedException) {
        if (context.conflictAlgorithm == CONFLICT_IGNORE) {
          throw exception
        }
        BulkEntityOutcome.FAILED
      }
      when (entityOutcome) {
        BulkEntityOutcome.APPLIED -> outcome = BulkOperationOutcome.APPLIED
        BulkEntityOutcome.IGNORED -> Unit
        BulkEntityOutcome.FAILED -> {
          outcome = BulkOperationOutcome.FAILED
          return false
        }
      }
    }
    if (isCancelled()) {
      throw CancellationException()
    }
    return outcome == BulkOperationOutcome.APPLIED
  }

  when {
    adapter is EntityRecursiveAdapter<*> && context.conflictAlgorithm == CONFLICT_IGNORE -> try {
      executeEntities()
    } finally {
      if (outcome == BulkOperationOutcome.APPLIED) {
        context.sendTableTriggers(adapter)
      }
    }
    else -> context.executeInTransaction {
      executeEntities().also { applied ->
        if (applied) {
          context.sendTableTriggers(adapter)
        }
      }
    }
  }
  return outcome
}

internal fun executeBulkRxOperation(
  contextFactory: () -> OperationContext,
  operation: (context: OperationContext, isCancelled: () -> Boolean) -> BulkOperationOutcome
) = Completable.create { emitter ->
  try {
    val context = contextFactory()
    when (operation(context, emitter::isDisposed)) {
      BulkOperationOutcome.FAILED -> emitter.tryOnError(
        OperationFailedException("Bulk operation failed")
      )
      else -> emitter.onComplete()
    }
  } catch (exception: CancellationException) {
    emitter.tryOnError(exception)
  }
}
