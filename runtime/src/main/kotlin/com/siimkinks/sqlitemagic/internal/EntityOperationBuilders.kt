package com.siimkinks.sqlitemagic.internal

import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.NotNullable
import com.siimkinks.sqlitemagic.OperationConfigurationSnapshot
import com.siimkinks.sqlitemagic.OperationContext
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityOperationByColumnBuilder

abstract class OperationBuilder<R>(
  private val adapter: EntityAdapter<*>
) {
  var connection: DbConnection? = null
  var conflictAlgorithm: Int = CONFLICT_NONE
  var ignoreNullValues: Boolean = false

  fun usingConnection(connection: DbConnection): R {
    this.connection = connection
    return self()
  }

  fun conflictAlgorithm(conflictAlgorithm: Int): R {
    this.conflictAlgorithm = conflictAlgorithm
    return self()
  }

  fun ignoreNullValues(): R {
    this.ignoreNullValues = true
    return self()
  }

  internal fun configurationSnapshot() = OperationConfigurationSnapshot(
    connection = connection ?: SqliteMagic.getDefaultConnection(),
    conflictAlgorithm = conflictAlgorithm,
    ignoreNullValues = ignoreNullValues
  )

  internal fun newContext(
    configuration: OperationConfigurationSnapshot = configurationSnapshot()
  ) = OperationContext(
    adapter = adapter,
    configuration = configuration
  )

  @Suppress("UNCHECKED_CAST")
  protected fun self(): R = this as R
}

abstract class DefaultIdentityBuilder<R, M>(
  private val adapter: EntityDefaultIdentityAdapter<M>
) : OperationBuilder<R>(adapter), EntityOperationByColumnBuilder<R> {
  private var configuredByColumn: IdentityColumn<M>? = null

  override fun <C : Unique<NotNullable>> byColumn(column: C): R {
    @Suppress("UNCHECKED_CAST")
    configuredByColumn = column as IdentityColumn<M>
    return self()
  }

  internal fun identitySelection() = IdentitySelection(
    column = configuredByColumn ?: adapter.defaultIdentityColumn,
    usesDefault = configuredByColumn == null
  )
}

internal data class IdentitySelection<M>(
  val column: IdentityColumn<M>,
  val usesDefault: Boolean
)

internal fun neverCancelled() = false
