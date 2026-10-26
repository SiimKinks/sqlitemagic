package com.siimkinks.sqlitemagic.internal

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.UpdateExecutors
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Completable

@InternalSqliteMagicApi
class UpdateBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entity: M
) : DefaultIdentityBuilder<EntityUpdateBuilder, M>(adapter), EntityUpdateBuilder {
  override fun execute() = perform(operation = update(identityColumnName()))

  override fun observe() = observeUpdate(
    entity = entity,
    update = update(identityColumnName())
  )

  private fun update(columnName: String) = update(
    adapter = adapter,
    entity = entity,
    columnName = columnName
  )
}

@InternalSqliteMagicApi
class UpdateByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M
) : OperationBuilder<EntityUpdateByColumnBuilder<M>>(adapter), EntityUpdateByColumnBuilder<M> {
  override fun execute(byColumn: Unique<M>) = perform(operation = update(byColumn))

  override fun observe(byColumn: Unique<M>) = observeUpdate(
    entity = entity,
    update = update(byColumn)
  )

  private fun update(byColumn: Unique<M>) = update(
    adapter = adapter,
    entity = entity,
    columnName = adapter.identityColumnName(byColumn)
  )
}

@InternalSqliteMagicApi
class BulkUpdateBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entities: Iterable<M>
) : DefaultIdentityBuilder<EntityBulkUpdateBuilder, M>(adapter), EntityBulkUpdateBuilder {
  override fun execute() = executeBulk(update(identityColumnName()))

  override fun observe() = observeBulk(update(identityColumnName()))

  private fun update(columnName: String) = bulkUpdate(
    adapter = adapter,
    entities = entities,
    columnName = columnName
  )
}

@InternalSqliteMagicApi
class BulkUpdateByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Iterable<M>
) : OperationBuilder<EntityBulkUpdateByColumnBuilder<M>>(adapter), EntityBulkUpdateByColumnBuilder<M> {
  override fun execute(byColumn: Unique<M>) = executeBulk(update(byColumn))

  override fun observe(byColumn: Unique<M>) = observeBulk(update(byColumn))

  private fun update(byColumn: Unique<M>) = bulkUpdate(
    adapter = adapter,
    entities = entities,
    columnName = adapter.identityColumnName(byColumn)
  )
}

private fun <M> update(
  adapter: EntityIdentityAdapter<M>,
  entity: M,
  columnName: String
) = { context: OperationContext ->
  UpdateExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = context,
    columnName = columnName
  )
}

private fun <M> bulkUpdate(
  adapter: EntityIdentityAdapter<M>,
  entities: Iterable<M>,
  columnName: String
) = { context: OperationContext, isCancelled: () -> Boolean ->
  UpdateExecutors.executeBulk(
    adapter = adapter,
    entities = entities,
    columnName = columnName,
    context = context,
    isCancelled = isCancelled
  )
}

private fun OperationBuilder<*>.observeUpdate(entity: Any?, update: (OperationContext) -> Boolean) =
  configurationSnapshot().let { configuration ->
    Completable.fromAction {
      val updated = perform(
        operation = update,
        configuration = configuration
      )
      if (!updated && configuration.conflictAlgorithm != CONFLICT_IGNORE) {
        throw OperationFailedException("Failed to update $entity")
      }
    }
  }
