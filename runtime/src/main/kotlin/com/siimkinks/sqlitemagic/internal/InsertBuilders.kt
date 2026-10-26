package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.InsertExecutors
import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import io.reactivex.Completable
import io.reactivex.Single

@InternalSqliteMagicApi
class InsertBuilder<M>(
  adapter: EntityAdapter<M>,
  entity: M
) : OperationBuilder<EntityInsertBuilder>(adapter), EntityInsertBuilder {
  private val insert = { context: OperationContext ->
    InsertExecutors.execute(
      adapter = adapter,
      entity = entity,
      context = context
    )
  }

  override fun execute() = perform(operation = insert)

  override fun observe(): Single<EntityInsertResult> = observeSingle(insert)
}

@InternalSqliteMagicApi
class BulkInsertBuilder<M>(
  adapter: EntityAdapter<M>,
  entities: Iterable<M>
) : OperationBuilder<EntityBulkInsertBuilder>(adapter), EntityBulkInsertBuilder {
  private val insert = { context: OperationContext, isCancelled: () -> Boolean ->
    InsertExecutors.executeBulk(
      adapter = adapter,
      entities = entities,
      context = context,
      isCancelled = isCancelled
    )
  }

  override fun execute() = executeBulk(insert)

  override fun observe(): Completable = observeBulk(insert)
}
