package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.BulkOperationOutcome
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.PersistExecutors
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.executeBulkRxOperation
import io.reactivex.Completable
import io.reactivex.Single

class PersistBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entity: M
) : DefaultIdentityBuilder<EntityPersistBuilder, M>(adapter), EntityPersistBuilder {
  override fun execute(): EntityPersistResult = execute(
    configuration = configurationSnapshot(),
    selection = identitySelection()
  )

  override fun observe(): Single<EntityPersistResult> {
    val configuration = configurationSnapshot()
    val selection = identitySelection()
    return Single.fromCallable { execute(configuration, selection) }
  }

  private fun execute(
    configuration: OperationConfigurationSnapshot,
    selection: IdentitySelection<M>
  ) = PersistExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = newContext(configuration),
    byColumn = selection.column,
    defaultIdentity = selection.usesDefault
  )
}

class PersistByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M
) : OperationBuilder<EntityPersistByColumnBuilder<M>>(adapter), EntityPersistByColumnBuilder<M> {
  override fun <D> execute(byColumn: D): EntityPersistResult
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> =
    PersistExecutors.execute(
      adapter = adapter,
      entity = entity,
      context = newContext(),
      byColumn = byColumn,
      defaultIdentity = false
    )

  override fun <D> observe(byColumn: D): Single<EntityPersistResult>
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> {
    val configuration = configurationSnapshot()
    return Single.fromCallable {
      PersistExecutors.execute(
        adapter = adapter,
        entity = entity,
        context = newContext(configuration),
        byColumn = byColumn,
        defaultIdentity = false
      )
    }
  }
}

class BulkPersistBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entities: Iterable<M>
) : DefaultIdentityBuilder<EntityBulkPersistBuilder, M>(adapter), EntityBulkPersistBuilder {
  override fun execute(): Boolean {
    val selection = identitySelection()
    return PersistExecutors.executeBulk(
      adapter = adapter,
      entities = entities,
      byColumn = selection.column,
      defaultIdentity = selection.usesDefault,
      context = newContext(),
      isCancelled = ::neverCancelled
    ) == BulkOperationOutcome.APPLIED
  }

  override fun observe(): Completable {
    val configuration = configurationSnapshot()
    val selection = identitySelection()
    return executeBulkRxOperation(
      contextFactory = { newContext(configuration) },
      operation = { context, isCancelled ->
        PersistExecutors.executeBulk(
          adapter = adapter,
          entities = entities,
          byColumn = selection.column,
          defaultIdentity = selection.usesDefault,
          context = context,
          isCancelled = isCancelled
        )
      }
    )
  }
}

class BulkPersistByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Iterable<M>
) : OperationBuilder<EntityBulkPersistByColumnBuilder<M>>(adapter),
  EntityBulkPersistByColumnBuilder<M> {
  override fun <D> execute(byColumn: D): Boolean
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> =
    PersistExecutors.executeBulk(
      adapter = adapter,
      entities = entities,
      byColumn = byColumn,
      defaultIdentity = false,
      context = newContext(),
      isCancelled = ::neverCancelled
    ) == BulkOperationOutcome.APPLIED

  override fun <D> observe(byColumn: D): Completable
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> {
    val configuration = configurationSnapshot()
    return executeBulkRxOperation(
      contextFactory = { newContext(configuration) },
      operation = { context, isCancelled ->
        PersistExecutors.executeBulk(
          adapter = adapter,
          entities = entities,
          byColumn = byColumn,
          defaultIdentity = false,
          context = context,
          isCancelled = isCancelled
        )
      }
    )
  }
}
