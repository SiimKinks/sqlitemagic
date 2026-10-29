package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.ReactiveOperation

data class StandardDeleteBuilders<T>(
  val delete: (T) -> EntityDeleteBuilder,
  val bulkDelete: (Collection<T>) -> EntityBulkDeleteBuilder,
  val deleteTable: () -> EntityDeleteTableBuilder
)

interface DeleteModelCase<T> : InsertModelCase<T> {
  fun executeDelete(value: T): Int

  fun observeDelete(
    adapter: ReactiveAdapter,
    value: T
  ): ReactiveOperation<Int>
}

interface BulkDeleteModelCase<T> : DeleteModelCase<T> {
  fun executeBulkDelete(values: Collection<T>): Int

  fun observeBulkDelete(
    adapter: ReactiveAdapter,
    values: Collection<T>
  ): ReactiveOperation<Int>
}

interface TableDeleteModelCase<T> : InsertModelCase<T> {
  fun executeTableDelete(): Int

  fun observeTableDelete(adapter: ReactiveAdapter): ReactiveOperation<Int>
}

interface ReferencedDeleteModelCase<T, R> : InsertModelCase<T>, RecursiveModelCase<T> {
  fun relatedDeleteValues(value: T): List<R>

  fun executeRelatedDelete(value: R): Int

  fun observeRelatedDelete(
    adapter: ReactiveAdapter,
    value: R
  ): ReactiveOperation<Int>

  fun executeRelatedBulkDelete(values: Collection<R>): Int

  fun observeRelatedBulkDelete(
    adapter: ReactiveAdapter,
    values: Collection<R>
  ): ReactiveOperation<Int>

  fun executeRelatedTableDelete(): Int

  fun observeRelatedTableDelete(adapter: ReactiveAdapter): ReactiveOperation<Int>

  override fun relatedValues(value: T): List<*> = relatedDeleteValues(value)
}

interface StandardDeleteModelCase<T> : DeleteModelCase<T> {
  fun delete(value: T): EntityDeleteBuilder

  override fun executeDelete(value: T) = delete(value)
    .execute()

  override fun observeDelete(
    adapter: ReactiveAdapter,
    value: T
  ) = adapter.observe(delete(value))
}

interface StandardTableDeleteModelCase<T> : TableDeleteModelCase<T> {
  fun deleteTable(): EntityDeleteTableBuilder

  override fun executeTableDelete() = deleteTable()
    .execute()

  override fun observeTableDelete(adapter: ReactiveAdapter) = adapter.observe(deleteTable())
}

interface StandardBulkDeleteModelCase<T> :
  BulkDeleteModelCase<T>,
  StandardDeleteModelCase<T>,
  StandardTableDeleteModelCase<T> {
  val deleteBuilders: StandardDeleteBuilders<T>

  override fun delete(value: T) = deleteBuilders.delete(value)

  override fun executeBulkDelete(values: Collection<T>) = deleteBuilders
    .bulkDelete(values)
    .execute()

  override fun observeBulkDelete(
    adapter: ReactiveAdapter,
    values: Collection<T>
  ) = adapter.observe(deleteBuilders.bulkDelete(values))

  override fun deleteTable() = deleteBuilders
    .deleteTable()
}
