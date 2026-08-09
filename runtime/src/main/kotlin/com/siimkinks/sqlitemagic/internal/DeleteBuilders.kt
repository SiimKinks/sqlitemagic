package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.DeleteExecutors
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import io.reactivex.Single

class DeleteBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M,
  private val byColumn: IdentityColumn<M>
) : OperationBuilder<EntityDeleteBuilder>(adapter), EntityDeleteBuilder {
  override fun execute() = DeleteExecutors.execute(
    adapter = adapter,
    entities = listOf(entity),
    byColumn = byColumn,
    context = newContext()
  )

  override fun observe(): Single<Int> {
    val configuration = configurationSnapshot()
    return Single.fromCallable {
      DeleteExecutors.execute(
        adapter = adapter,
        entities = listOf(entity),
        byColumn = byColumn,
        context = newContext(configuration)
      )
    }
  }
}

class DeleteByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M
) : OperationBuilder<EntityDeleteByColumnBuilder<M>>(adapter), EntityDeleteByColumnBuilder<M> {
  override fun <D> execute(byColumn: D): Int
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> =
    DeleteExecutors.execute(
      adapter = adapter,
      entities = listOf(entity),
      byColumn = byColumn,
      context = newContext()
    )

  override fun <D> observe(byColumn: D): Single<Int>
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> {
    val configuration = configurationSnapshot()
    return Single.fromCallable {
      DeleteExecutors.execute(
        adapter = adapter,
        entities = listOf(entity),
        byColumn = byColumn,
        context = newContext(configuration)
      )
    }
  }
}

class BulkDeleteBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Collection<M>,
  private val byColumn: IdentityColumn<M>
) : OperationBuilder<EntityBulkDeleteBuilder>(adapter), EntityBulkDeleteBuilder {
  override fun execute() = DeleteExecutors.execute(
    adapter = adapter,
    entities = entities,
    byColumn = byColumn,
    context = newContext()
  )

  override fun observe(): Single<Int> {
    val configuration = configurationSnapshot()
    return Single.fromCallable {
      DeleteExecutors.execute(
        adapter = adapter,
        entities = entities,
        byColumn = byColumn,
        context = newContext(configuration)
      )
    }
  }
}

class BulkDeleteByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Collection<M>
) : OperationBuilder<EntityBulkDeleteByColumnBuilder<M>>(adapter), EntityBulkDeleteByColumnBuilder<M> {
  override fun <D> execute(byColumn: D): Int
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> =
    DeleteExecutors.execute(
      adapter = adapter,
      entities = entities,
      byColumn = byColumn,
      context = newContext()
    )

  override fun <D> observe(byColumn: D): Single<Int>
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> {
    val configuration = configurationSnapshot()
    return Single.fromCallable {
      DeleteExecutors.execute(
        adapter = adapter,
        entities = entities,
        byColumn = byColumn,
        context = newContext(configuration)
      )
    }
  }
}

class DeleteTableBuilder<M>(
  private val adapter: EntityAdapter<M>
) : OperationBuilder<EntityDeleteTableBuilder>(adapter), EntityDeleteTableBuilder {
  override fun execute() = execute(configurationSnapshot())

  override fun observe(): Single<Int> {
    val configuration = configurationSnapshot()
    return Single.fromCallable { execute(configuration) }
  }

  private fun execute(configuration: OperationConfigurationSnapshot) =
    DeleteExecutors.deleteTable(
      adapter = adapter,
      context = newContext(configuration)
    )
}
