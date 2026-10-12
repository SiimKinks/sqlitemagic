package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.IdentityColumn

internal object DeleteExecutors {
  fun <M> execute(
    adapter: EntityIdentityAdapter<M>,
    entities: Collection<M>,
    byColumn: IdentityColumn<M>,
    context: OperationContext
  ): Int = context.executeInTransaction {
    val identities = entities.map { entity ->
      adapter.identity(
        entity = entity,
        byColumn = byColumn
      )
    }
    if (identities.isEmpty()) {
      return@executeInTransaction 0
    }
    val sql = buildString {
      append("DELETE FROM ")
      append(adapter.tableName)
      append(" WHERE ")
      append(identities.first().columnName)
      append(" IN (")
      repeat(identities.size) { index ->
        if (index > 0) append(',')
        append('?')
      }
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
              identities.forEachIndexed { index, entityIdentity ->
                statement.bindString(
                  index + 1,
                  entityIdentity.serializedValue
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
  ): Int = context
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
