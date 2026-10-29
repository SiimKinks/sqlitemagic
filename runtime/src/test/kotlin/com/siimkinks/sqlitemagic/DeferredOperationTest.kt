package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeferredOperation
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import org.junit.Test

internal class DeferredOperationTest {
  @Test
  fun `every builder defers the operation that its execute runs`() {
    val adapter = TestAdapter()
    fun entities() = listOf(
      TestEntity(),
      TestEntity(
        id = "id-2",
        key = "key-2"
      )
    )

    val cases = listOf(
      Case<EntityInsertBuilder>(
        label = "insert",
        builder = { adapter.insert(TestEntity()) },
        execute = EntityInsertBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityBulkInsertBuilder>(
        label = "bulk insert",
        builder = { adapter.bulkInsert(entities()) },
        execute = EntityBulkInsertBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityUpdateBuilder>(
        label = "update",
        builder = { adapter.update(TestEntity()) },
        execute = EntityUpdateBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityUpdateByColumnBuilder<TestEntity>>(
        label = "update by column",
        builder = { adapter.updateByColumn(TestEntity()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        defer = { builder ->
          ReactiveAdapterSupport.deferred(
            builder = builder,
            byColumn = TestSchema.key
          )
        }
      ),
      Case<EntityBulkUpdateBuilder>(
        label = "bulk update",
        builder = { adapter.bulkUpdate(entities()) },
        execute = EntityBulkUpdateBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityBulkUpdateByColumnBuilder<TestEntity>>(
        label = "bulk update by column",
        builder = { adapter.bulkUpdateByColumn(entities()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        defer = { builder ->
          ReactiveAdapterSupport.deferred(
            builder = builder,
            byColumn = TestSchema.key
          )
        }
      ),
      Case<EntityPersistBuilder>(
        label = "persist",
        builder = { adapter.persist(TestEntity()) },
        execute = EntityPersistBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityPersistByColumnBuilder<TestEntity>>(
        label = "persist by column",
        builder = { adapter.persistByColumn(TestEntity()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        defer = { builder ->
          ReactiveAdapterSupport.deferred(
            builder = builder,
            byColumn = TestSchema.key
          )
        }
      ),
      Case<EntityBulkPersistBuilder>(
        label = "bulk persist",
        builder = { adapter.bulkPersist(entities()) },
        execute = EntityBulkPersistBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityBulkPersistByColumnBuilder<TestEntity>>(
        label = "bulk persist by column",
        builder = { adapter.bulkPersistByColumn(entities()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        defer = { builder ->
          ReactiveAdapterSupport.deferred(
            builder = builder,
            byColumn = TestSchema.key
          )
        }
      ),
      Case<EntityDeleteBuilder>(
        label = "delete",
        builder = { adapter.delete(TestEntity()) },
        execute = EntityDeleteBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityDeleteByColumnBuilder<TestEntity>>(
        label = "delete by column",
        builder = { adapter.deleteByColumn(TestEntity()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        defer = { builder ->
          ReactiveAdapterSupport.deferred(
            builder = builder,
            byColumn = TestSchema.key
          )
        }
      ),
      Case<EntityBulkDeleteBuilder>(
        label = "bulk delete",
        builder = { adapter.bulkDelete(entities()) },
        execute = EntityBulkDeleteBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      ),
      Case<EntityBulkDeleteByColumnBuilder<TestEntity>>(
        label = "bulk delete by column",
        builder = { adapter.bulkDeleteByColumn(entities()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        defer = { builder ->
          ReactiveAdapterSupport.deferred(
            builder = builder,
            byColumn = TestSchema.key
          )
        }
      ),
      Case<EntityDeleteTableBuilder>(
        label = "delete table",
        builder = { adapter.deleteTable() },
        execute = EntityDeleteTableBuilder::execute,
        defer = ReactiveAdapterSupport::deferred
      )
    )

    for (case in cases) {
      val (executed, deferred) = listOf(false, true).map(case::run)

      assertWithMessage(case.label)
        .that(deferred)
        .isEqualTo(executed)
    }
  }

  private data class Outcome(
    val result: Any?,
    val statements: List<String>,
    val triggers: List<Set<String>>
  )

  private class Case<B : ConnectionProvidedOperation<B>>(
    val label: String,
    val builder: () -> B,
    val execute: (B) -> Any?,
    val defer: (B) -> DeferredOperation<*>
  ) {
    fun run(deferred: Boolean): Outcome {
      val connection = newConnection()
      val builder = builder().usingConnection(connection.connection)
      val result = when {
        deferred -> defer(builder).execute(isCancelled = { false })
        else -> execute(builder)
      }
      return Outcome(
        result = result,
        statements = connection.statementSql,
        triggers = connection.triggers
      )
    }
  }
}
