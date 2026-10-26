package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.BulkEntityOutcome.APPLIED
import com.siimkinks.sqlitemagic.BulkEntityOutcome.IGNORED
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.recursiveOrNull

internal object InsertExecutors {
  fun <M> execute(
    adapter: EntityAdapter<M>,
    entity: M,
    context: OperationContext,
    standalone: Boolean = true
  ): EntityInsertResult {
    val recursiveAdapter = when {
      standalone -> adapter.recursiveOrNull()
      else -> null
    }
    return when (recursiveAdapter) {
      null -> insert(
        adapter = adapter,
        recursiveAdapter = null,
        entity = entity,
        context = context,
        standalone = standalone
      )
      else -> context.executeInTransaction {
        insert(
          adapter = adapter,
          recursiveAdapter = recursiveAdapter,
          entity = entity,
          context = context,
          standalone = true
        )
      }
    }
  }

  private fun <M> insert(
    adapter: EntityAdapter<M>,
    recursiveAdapter: EntityRecursiveAdapter<M>?,
    entity: M,
    context: OperationContext,
    standalone: Boolean
  ): EntityInsertResult {
    val relationshipsInserted = recursiveAdapter
      ?.insertRelationships(
        entity = entity,
        operations = RelationshipOperations(context)
      )
      ?: true
    return when {
      !relationshipsInserted && context.conflictAlgorithm == CONFLICT_IGNORE -> EntityInsertResult.Ignored
      !relationshipsInserted -> throw OperationFailedException("Failed to insert relationship for $entity")
      else -> mapResult(
        adapter = adapter,
        rowId = executeInternal(
          adapter = adapter,
          entity = entity,
          context = context,
          logOperation = standalone
        ),
        conflictAlgorithm = context.conflictAlgorithm,
        failureMessage = "Failed to insert $entity"
      )
    }.also { result ->
      if (standalone && (result is EntityInsertResult.Inserted || recursiveAdapter != null)) {
        context.sendTableTriggers(adapter)
      }
    }
  }

  private fun <M> executeInternal(
    adapter: EntityAdapter<M>,
    entity: M,
    context: OperationContext,
    logOperation: Boolean
  ) = context.executeMutation(
    adapter = adapter,
    operation = mutation@{
      if (logOperation) {
        context.logInsert(entity = entity)
      }
      val rowId = context.withInsertStatement(adapter.insertSql) { statement ->
        adapter.bindToInsertStatement(
          statement = statement,
          entity = entity,
          generatedRelationshipIds = context.generatedRelationshipIds
        )
        executeStatement(
          statement = statement,
          withoutRowId = adapter.withoutRowId,
          execution = this@mutation
        )
      }
      complete(
        adapter = adapter,
        entity = entity,
        rowId = rowId,
        logOperation = logOperation
      )
    }
  )

  fun <M> executeIgnoringNullValues(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext
  ) = context.executeMutation(
    adapter = adapter,
    operation = mutation@{
      adapter.bindNotNullForInsert(
        entity = entity,
        values = context.bindValues,
        generatedRelationshipIds = context.generatedRelationshipIds
      )
      val rowId = context
        .compileInsertStatement(adapter.tableName)
        .use { statement ->
          executeStatement(
            statement = statement,
            withoutRowId = adapter.withoutRowId,
            execution = this@mutation
          )
        }
      complete(
        adapter = adapter,
        entity = entity,
        rowId = rowId,
        logOperation = false
      )
    }
  )

  private fun executeStatement(
    statement: SupportSQLiteStatement,
    withoutRowId: Boolean,
    execution: EntityMutationExecution
  ) = when {
    withoutRowId -> when {
      execution.executeUpdateDelete(statement) > 0 -> 0L
      else -> -1L
    }
    else -> execution.executeInsert(statement)
  }

  private fun <M> complete(
    adapter: EntityAdapter<M>,
    entity: M,
    rowId: Long,
    logOperation: Boolean
  ): Long {
    if (rowId != -1L && !adapter.withoutRowId) {
      (adapter as? EntityGeneratedIdAdapter<M>)?.assignGeneratedId(
        entity = entity,
        rowId = rowId
      )
    }
    if (logOperation) {
      logInsertId(rowId = rowId)
    }
    return rowId
  }

  fun mapResult(
    adapter: EntityAdapter<*>,
    rowId: Long,
    conflictAlgorithm: Int,
    failureMessage: String
  ) = when (rowId) {
    -1L if conflictAlgorithm == CONFLICT_IGNORE -> EntityInsertResult.Ignored
    -1L -> throw OperationFailedException(failureMessage)
    else -> EntityInsertResult.Inserted(
      when {
        adapter.withoutRowId -> null
        else -> rowId
      }
    )
  }

  fun <M> executeBulk(
    adapter: EntityAdapter<M>,
    entities: Iterable<M>,
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
        context = context.childWithoutTableTriggers()
      )
      when (result) {
        is EntityInsertResult.Inserted -> APPLIED
        EntityInsertResult.Ignored -> IGNORED
      }
    }
  )
}
