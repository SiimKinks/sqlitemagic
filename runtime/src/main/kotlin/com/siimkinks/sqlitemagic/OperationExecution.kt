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

internal fun executeBulkOperation(
  adapter: EntityAdapter<*>,
  context: OperationContext,
  operation: () -> Boolean,
  isSuccessful: () -> Boolean
): Boolean = when {
  adapter is EntityRecursiveAdapter<*> && context.conflictAlgorithm == CONFLICT_IGNORE -> try {
    operation()
  } finally {
    if (isSuccessful()) {
      context.sendTableTriggers(adapter)
    }
  }
  else -> context.executeInTransaction {
    operation().also { successful ->
      if (successful) {
        context.sendTableTriggers(adapter)
      }
    }
  }
}

internal fun executeBulkRxOperation(
  contextFactory: () -> OperationContext,
  operation: (context: OperationContext, isCancelled: () -> Boolean) -> Boolean
) = Completable.create { emitter ->
  try {
    val context = contextFactory()
    val successful = operation(context, emitter::isDisposed)
    when {
      !successful && context.conflictAlgorithm != CONFLICT_IGNORE -> emitter.tryOnError(
        OperationFailedException("Bulk operation failed")
      )
      else -> emitter.onComplete()
    }
  } catch (exception: CancellationException) {
    emitter.tryOnError(exception)
  }
}
