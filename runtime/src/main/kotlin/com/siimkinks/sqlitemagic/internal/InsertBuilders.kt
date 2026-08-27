package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.BulkOperationOutcome
import com.siimkinks.sqlitemagic.InsertExecutors
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.executeBulkRxOperation
import io.reactivex.Completable
import io.reactivex.Single

class InsertBuilder<M>(
    private val adapter: EntityAdapter<M>,
    private val entity: M
) : OperationBuilder<EntityInsertBuilder>(adapter), EntityInsertBuilder {
  override fun execute() = execute(configurationSnapshot())

  override fun observe(): Single<EntityInsertResult> {
    val configuration = configurationSnapshot()
    return Single.fromCallable { execute(configuration) }
  }

  private fun execute(configuration: OperationConfigurationSnapshot): EntityInsertResult =
    InsertExecutors.execute(
      adapter = adapter,
      entity = entity,
      context = newContext(configuration)
    )
}

class BulkInsertBuilder<M>(
    private val adapter: EntityAdapter<M>,
    private val entities: Iterable<M>
) : OperationBuilder<EntityBulkInsertBuilder>(adapter), EntityBulkInsertBuilder {
  override fun execute() = InsertExecutors.executeBulk(
    adapter = adapter,
    entities = entities,
    context = newContext(),
    isCancelled = ::neverCancelled
  ) == BulkOperationOutcome.APPLIED

  override fun observe(): Completable {
    val configuration = configurationSnapshot()
    return executeBulkRxOperation(
      contextFactory = { newContext(configuration) },
      operation = { context, isCancelled ->
        InsertExecutors.executeBulk(
          adapter = adapter,
          entities = entities,
          context = context,
          isCancelled = isCancelled
        )
      }
    )
  }
}
