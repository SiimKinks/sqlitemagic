package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.PersistExecutors
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder

@InternalSqliteMagicApi
class PersistBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entity: M
) : DefaultIdentityBuilder<EntityPersistBuilder, M>(adapter), EntityPersistBuilder {
  override fun execute() = perform(operation = persist(identityColumnName()))

  internal fun deferred() = defer(persist(identityColumnName()))

  private fun persist(columnName: String) = persist(
    adapter = adapter,
    entity = entity,
    columnName = columnName
  )
}

@InternalSqliteMagicApi
class PersistByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M
) : OperationBuilder<EntityPersistByColumnBuilder<M>>(adapter), EntityPersistByColumnBuilder<M> {
  override fun execute(byColumn: Unique<M>) = perform(operation = persist(byColumn))

  internal fun deferred(byColumn: Unique<M>) = defer(persist(byColumn))

  private fun persist(byColumn: Unique<M>) = persist(
    adapter = adapter,
    entity = entity,
    columnName = adapter.identityColumnName(byColumn)
  )
}

@InternalSqliteMagicApi
class BulkPersistBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entities: Iterable<M>
) : DefaultIdentityBuilder<EntityBulkPersistBuilder, M>(adapter), EntityBulkPersistBuilder {
  override fun execute() = executeBulk(persist(identityColumnName()))

  internal fun deferred() = deferBulk(persist(identityColumnName()))

  private fun persist(columnName: String) = bulkPersist(
    adapter = adapter,
    entities = entities,
    columnName = columnName
  )
}

@InternalSqliteMagicApi
class BulkPersistByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Iterable<M>
) : OperationBuilder<EntityBulkPersistByColumnBuilder<M>>(adapter), EntityBulkPersistByColumnBuilder<M> {
  override fun execute(byColumn: Unique<M>) = executeBulk(persist(byColumn))

  internal fun deferred(byColumn: Unique<M>) = deferBulk(persist(byColumn))

  private fun persist(byColumn: Unique<M>) = bulkPersist(
    adapter = adapter,
    entities = entities,
    columnName = adapter.identityColumnName(byColumn)
  )
}

private fun <M> persist(
  adapter: EntityIdentityAdapter<M>,
  entity: M,
  columnName: String
) = { context: OperationContext ->
  PersistExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = context,
    columnName = columnName
  )
}

private fun <M> bulkPersist(
  adapter: EntityIdentityAdapter<M>,
  entities: Iterable<M>,
  columnName: String
) = { context: OperationContext, isCancelled: () -> Boolean ->
  PersistExecutors.executeBulk(
    adapter = adapter,
    entities = entities,
    columnName = columnName,
    context = context,
    isCancelled = isCancelled
  )
}
