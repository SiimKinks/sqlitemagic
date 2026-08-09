package com.siimkinks.sqlitemagic.internal

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.UpdateExecutors
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.executeBulkRxOperation
import io.reactivex.Completable

class UpdateBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entity: M
) : DefaultIdentityBuilder<EntityUpdateBuilder, M>(adapter), EntityUpdateBuilder {
  override fun execute() = execute(
    configuration = configurationSnapshot(),
    selection = identitySelection()
  )

  override fun observe(): Completable {
    val configuration = configurationSnapshot()
    val selection = identitySelection()
    return Completable.fromAction {
      val success = execute(
        configuration = configuration,
        selection = selection
      )
      if (!success && configuration.conflictAlgorithm != CONFLICT_IGNORE) {
        throw OperationFailedException("Failed to update $entity")
      }
    }
  }

  private fun execute(
    configuration: OperationConfigurationSnapshot,
    selection: IdentitySelection<M>
  ) = UpdateExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = newContext(configuration),
    byColumn = selection.column,
    defaultIdentity = selection.usesDefault
  )
}

class UpdateByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entity: M
) : OperationBuilder<EntityUpdateByColumnBuilder<M>>(adapter), EntityUpdateByColumnBuilder<M> {
  override fun <D> execute(byColumn: D): Boolean
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> = execute(
    configuration = configurationSnapshot(),
    byColumn = byColumn
  )

  override fun <D> observe(byColumn: D): Completable
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> {
    val configuration = configurationSnapshot()
    return Completable.fromAction {
      val success = execute(
        configuration = configuration,
        byColumn = byColumn
      )
      if (!success && configuration.conflictAlgorithm != CONFLICT_IGNORE) {
        throw OperationFailedException("Failed to update $entity")
      }
    }
  }

  private fun execute(
    configuration: OperationConfigurationSnapshot,
    byColumn: IdentityColumn<M>
  ) = UpdateExecutors.execute(
    adapter = adapter,
    entity = entity,
    context = newContext(configuration),
    byColumn = byColumn,
    defaultIdentity = false
  )
}

class BulkUpdateBuilder<M>(
  private val adapter: EntityDefaultIdentityAdapter<M>,
  private val entities: Iterable<M>
) : DefaultIdentityBuilder<EntityBulkUpdateBuilder, M>(adapter), EntityBulkUpdateBuilder {
  override fun execute(): Boolean {
    val selection = identitySelection()
    return UpdateExecutors.executeBulk(
      adapter = adapter,
      entities = entities,
      byColumn = selection.column,
      defaultIdentity = selection.usesDefault,
      context = newContext(),
      isCancelled = ::neverCancelled
    )
  }

  override fun observe(): Completable {
    val configuration = configurationSnapshot()
    val selection = identitySelection()
    return executeBulkRxOperation(
      contextFactory = { newContext(configuration) },
      operation = { context, isCancelled ->
        UpdateExecutors.executeBulk(
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

class BulkUpdateByColumnBuilder<M>(
  private val adapter: EntityIdentityAdapter<M>,
  private val entities: Iterable<M>
) : OperationBuilder<EntityBulkUpdateByColumnBuilder<M>>(adapter), EntityBulkUpdateByColumnBuilder<M> {
  override fun <D> execute(byColumn: D): Boolean
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> =
    UpdateExecutors.executeBulk(
      adapter = adapter,
      entities = entities,
      byColumn = byColumn,
      defaultIdentity = false,
      context = newContext(),
      isCancelled = ::neverCancelled
    )

  override fun <D> observe(byColumn: D): Completable
      where D : Column<*, *, *, M, NotNullable>,
            D : Unique<NotNullable> {
    val configuration = configurationSnapshot()
    return executeBulkRxOperation(
      contextFactory = { newContext(configuration) },
      operation = { context, isCancelled ->
        UpdateExecutors.executeBulk(
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
