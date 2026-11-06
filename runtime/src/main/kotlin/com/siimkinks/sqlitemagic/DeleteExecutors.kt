package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter

internal object DeleteExecutors {
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entities: Collection<M>,
    columnName: String,
    context: OperationContext
  ) = when {
    entities.isEmpty() -> 0
    else -> context
      .executeMutation(
        adapter = adapter,
        deletes = true,
        operation = {
          context.withStatement(
            sql = {
              context.deleteSql(
                columnName = columnName,
                count = entities.size
              )
            }
          ) { statement, results ->
            entities.forEachIndexed { index, entity ->
              statement.bindStorageValue(
                position = index + 1,
                value = adapter.identity(
                  entity = entity,
                  columnName = columnName
                )
              )
            }
            executeUpdateDelete(
              statement = statement,
              results = results
            )
          }
        }
      )
      .also { deletedRowCount ->
        if (deletedRowCount > 0) {
          context.sendTableTriggers(
            adapter = adapter,
            deletes = true
          )
        }
      }
  }

  fun <M> deleteTable(
    adapter: EntityAdapter<M>,
    context: OperationContext
  ) = context
    .executeMutation(
      adapter = adapter,
      deletes = true,
      operation = {
        context.withStatement(
          sql = context::deleteTableSql,
          block = ::executeUpdateDelete
        )
      }
    )
    .also { deletedRowCount ->
      if (deletedRowCount > 0) {
        context.sendTableTriggers(
          adapter = adapter,
          deletes = true
        )
      }
    }
}
