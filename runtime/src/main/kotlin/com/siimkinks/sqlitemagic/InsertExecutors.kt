package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import java.util.concurrent.CancellationException

internal object InsertExecutors {
  @Suppress("UNCHECKED_CAST")
  private fun <M> generatedIdAdapter(adapter: EntityAdapter<M>) =
    adapter as? EntityGeneratedIdAdapter<M>

  fun <M> execute(
    adapter: EntityAdapter<M>,
    entity: M,
    context: OperationContext,
    includeRelationships: Boolean = true,
    publishTriggers: Boolean = true,
    logOperation: Boolean = true
  ): EntityInsertResult {
    val recursiveAdapter = adapter as? EntityRecursiveAdapter<M>
    val includesRecursiveRelationships = includeRelationships && recursiveAdapter != null
    val operation = {
      val relationshipsInserted = when {
        includesRecursiveRelationships -> recursiveAdapter.insertRelationships(
          entity = entity,
          operations = RelationshipOperations(context)
        )
        else -> true
      }
      when {
        !relationshipsInserted && context.conflictAlgorithm == CONFLICT_IGNORE -> EntityInsertResult.Ignored
        !relationshipsInserted -> throw OperationFailedException("Failed to insert relationship for $entity")
        else -> mapResult(
          adapter = adapter,
          rowId = executeInternal(
            adapter = adapter,
            entity = entity,
            context = context,
            logOperation = logOperation
          ),
          conflictAlgorithm = context.conflictAlgorithm,
          failureMessage = "Failed to insert $entity"
        )
      }.also { result ->
        if (publishTriggers && (result is EntityInsertResult.Inserted || includesRecursiveRelationships)) {
          context.sendTableTriggers(adapter)
        }
      }
    }
    return try {
      when {
        includesRecursiveRelationships -> context.executeInTransaction(operation)
        else -> operation()
      }
    } catch (exception: OperationFailedException) {
      context.logError(exception = exception, message = "Operation failed")
      throw exception
    } catch (exception: Exception) {
      throw OperationFailedException("Failed to insert $entity", exception)
    }
  }

  private fun <M> executeInternal(
    adapter: EntityAdapter<M>,
    entity: M,
    context: OperationContext,
    logOperation: Boolean
  ): Long {
    if (logOperation) {
      context.logInsert(entity = entity)
    }
    val rowId = context
      .operationHelper(
        operation = OperationHelper.Op.INSERT,
        operationByColumns = null
      )
      .use { helper ->
        val statement = helper.getInsertStatement(
          adapter.tableName,
          adapter.insertSql,
          context.entityDbManager()
        )
        synchronized(statement) {
          adapter.bindToInsertStatement(
            statement = statement,
            entity = entity,
            generatedRelationshipIds = context.generatedRelationshipIds
          )
          executeStatement(
            statement = statement,
            withoutRowId = adapter.withoutRowId
          )
        }
      }
    return complete(
      adapter = adapter,
      entity = entity,
      context = context,
      rowId = rowId,
      logOperation = logOperation
    )
  }

  fun <M> executeIgnoringNullValues(
    adapter: EntityIdentityAdapter<M>,
    entity: M,
    context: OperationContext,
    logOperation: Boolean
  ): Long {
    if (logOperation) {
      context.logInsert(entity = entity)
    }
    adapter.bindNotNullForInsert(
      entity = entity,
      values = context.bindValues,
      generatedRelationshipIds = context.generatedRelationshipIds
    )
    val rowId = when {
      context.bindValues.isEmpty -> context
        .entityDbManager()
        .compileStatement(
          "INSERT${context.conflictValue()} INTO ${adapter.tableName} DEFAULT VALUES"
        )
        .use { statement ->
          executeStatement(
            statement = statement,
            withoutRowId = adapter.withoutRowId
          )
        }
      else -> context
        .variableArgsOperationHelper
        .compileStatement(
          OperationHelper.Op.INSERT,
          adapter.tableName,
          adapter.maxColumns,
          context.bindValues,
          "",
          context.entityDbManager()
        )
        .use { statement ->
          executeStatement(
            statement = statement,
            withoutRowId = adapter.withoutRowId
          )
        }
    }
    return complete(
      adapter = adapter,
      entity = entity,
      context = context,
      rowId = rowId,
      logOperation = logOperation
    )
  }

  private fun executeStatement(
    statement: SupportSQLiteStatement,
    withoutRowId: Boolean
  ) = when {
    withoutRowId -> if (statement.executeUpdateDelete() > 0) 0L else -1L
    else -> statement.executeInsert()
  }

  private fun <M> complete(
    adapter: EntityAdapter<M>,
    entity: M,
    context: OperationContext,
    rowId: Long,
    logOperation: Boolean
  ): Long {
    if (rowId != -1L && !adapter.withoutRowId) {
      generatedIdAdapter(adapter)?.assignGeneratedId(
        entity = entity,
        rowId = rowId
      )
    }
    if (logOperation) {
      context.logInsertId(rowId = rowId)
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
    else -> EntityInsertResult.Inserted(if (adapter.withoutRowId) null else rowId)
  }

  fun <M> executeBulk(
    adapter: EntityAdapter<M>,
    entities: Iterable<M>,
    context: OperationContext,
    isCancelled: () -> Boolean
  ): Boolean {
    var inserted = false

    fun executeEntities(): Boolean {
      for (entity in entities) {
        if (isCancelled()) {
          throw CancellationException()
        }
        val result = try {
          execute(
            adapter = adapter,
            entity = entity,
            context = context.childWithoutTableTriggers()
          )
        } catch (exception: OperationFailedException) {
          if (context.conflictAlgorithm == CONFLICT_IGNORE) {
            throw exception
          }
          return false
        }
        if (result is EntityInsertResult.Inserted) {
          inserted = true
        }
      }
      if (isCancelled()) {
        throw CancellationException()
      }
      return inserted
    }

    return executeBulkOperation(
      adapter = adapter,
      context = context,
      operation = ::executeEntities,
      isSuccessful = { inserted }
    )
  }
}

private fun OperationContext.conflictValue() =
  ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm]
