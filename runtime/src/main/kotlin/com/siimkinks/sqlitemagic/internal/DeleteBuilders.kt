package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.DeleteExecutors
import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder

@InternalSqliteMagicApi
class DeleteBuilder<M>(
  adapter: EntityDefaultIdentityAdapter<M>,
  entity: M
) : OperationBuilder<EntityDeleteBuilder>(adapter), EntityDeleteBuilder {
  private val delete = delete(
    adapter = adapter,
    entities = listOf(entity),
    columnName = adapter.defaultIdentityColumnName
  )

  override fun execute() = perform(operation = delete)

  internal fun deferred() = defer(delete)
}

@InternalSqliteMagicApi
class DeleteByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M
) : OperationBuilder<EntityDeleteByColumnBuilder<M>>(adapter), EntityDeleteByColumnBuilder<M> {
  override fun execute(byColumn: Unique<M>) = perform(operation = delete(byColumn))

  internal fun deferred(byColumn: Unique<M>) = defer(delete(byColumn))

  private fun delete(byColumn: Unique<M>) = delete(
    adapter = adapter,
    entities = listOf(entity),
    columnName = adapter.identityColumnName(byColumn)
  )
}

@InternalSqliteMagicApi
class BulkDeleteBuilder<M>(
  adapter: EntityDefaultIdentityAdapter<M>,
  entities: Collection<M>
) : OperationBuilder<EntityBulkDeleteBuilder>(adapter), EntityBulkDeleteBuilder {
  private val delete = delete(
    adapter = adapter,
    entities = entities,
    columnName = adapter.defaultIdentityColumnName
  )

  override fun execute() = perform(operation = delete)

  internal fun deferred() = defer(delete)
}

@InternalSqliteMagicApi
class BulkDeleteByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Collection<M>
) : OperationBuilder<EntityBulkDeleteByColumnBuilder<M>>(adapter), EntityBulkDeleteByColumnBuilder<M> {
  override fun execute(byColumn: Unique<M>) = perform(operation = delete(byColumn))

  internal fun deferred(byColumn: Unique<M>) = defer(delete(byColumn))

  private fun delete(byColumn: Unique<M>) = delete(
    adapter = adapter,
    entities = entities,
    columnName = adapter.identityColumnName(byColumn)
  )
}

@InternalSqliteMagicApi
class DeleteTableBuilder<M>(
  adapter: EntityAdapter<M>
) : OperationBuilder<EntityDeleteTableBuilder>(adapter), EntityDeleteTableBuilder {
  private val delete = { context: OperationContext ->
    DeleteExecutors.deleteTable(
      adapter = adapter,
      context = context
    )
  }

  override fun execute() = perform(operation = delete)

  internal fun deferred() = defer(delete)
}

private fun <M> delete(
  adapter: EntityIdentityAdapter<M>,
  entities: Collection<M>,
  columnName: String
) = { context: OperationContext ->
  DeleteExecutors.execute(
    adapter = adapter,
    entities = entities,
    columnName = columnName,
    context = context
  )
}
