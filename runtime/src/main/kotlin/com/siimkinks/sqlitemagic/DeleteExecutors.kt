package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter

internal object DeleteExecutors {
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entities: Collection<M>,
    columnName: String,
    context: OperationContext
  ) = context.executeInTransaction {
    val identities = entities.map { entity ->
      adapter.identity(
        entity = entity,
        columnName = columnName
      )
    }
    if (identities.isEmpty()) {
      return@executeInTransaction 0
    }
    val sql = buildString {
      append("DELETE FROM ")
      append(adapter.tableName)
      append(" WHERE ")
      append(columnName)
      append(" IN (")
      appendPlaceholders(identities.size)
      append(')')
    }

    context
      .executeMutation(
        adapter = adapter,
        operation = {
          context
            .entityDbManager()
            .compileStatement(sql)
            .use { statement ->
              identities.forEachIndexed { index, identity ->
                statement.bindStorageValue(
                  position = index + 1,
                  value = identity
                )
              }
              executeUpdateDelete(statement)
            }
        }
      )
      .also { deletedRowCount ->
        if (deletedRowCount > 0) {
          context.sendTableTriggers(adapter)
        }
      }
  }

  fun <M> deleteTable(
    adapter: EntityAdapter<M>,
    context: OperationContext
  ) = context
    .executeMutation(
      adapter = adapter,
      operation = {
        context
          .entityDbManager()
          .compileStatement("DELETE FROM ${adapter.tableName} WHERE 1")
          .use(::executeUpdateDelete)
      }
    )
    .also { deletedRowCount ->
      if (deletedRowCount > 0) {
        context.sendTableTriggers(adapter)
      }
    }
}
