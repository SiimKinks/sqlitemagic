package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import io.reactivex.Single

data class StandardDeleteBuilders<T>(
  val delete: (T) -> EntityDeleteBuilder,
  val bulkDelete: (Collection<T>) -> EntityBulkDeleteBuilder,
  val deleteTable: () -> EntityDeleteTableBuilder
)

interface DeleteModelCase<T> : InsertModelCase<T> {
  fun executeDelete(value: T): Int

  fun observeDelete(value: T): Single<Int>
}

interface BulkDeleteModelCase<T> : DeleteModelCase<T> {
  fun executeBulkDelete(values: Collection<T>): Int

  fun observeBulkDelete(values: Collection<T>): Single<Int>
}

interface TableDeleteModelCase<T> : InsertModelCase<T> {
  fun executeTableDelete(): Int

  fun observeTableDelete(): Single<Int>
}

interface ReferencedDeleteModelCase<T, R> : InsertModelCase<T>, RecursiveModelCase<T> {
  fun relatedDeleteValues(value: T): List<R>

  fun executeRelatedDelete(value: R): Int

  fun observeRelatedDelete(value: R): Single<Int>

  fun executeRelatedBulkDelete(values: Collection<R>): Int

  fun observeRelatedBulkDelete(values: Collection<R>): Single<Int>

  fun executeRelatedTableDelete(): Int

  fun observeRelatedTableDelete(): Single<Int>

  override fun relatedValues(value: T): List<*> = relatedDeleteValues(value)
}

interface StandardDeleteModelCase<T> : DeleteModelCase<T> {
  fun delete(value: T): EntityDeleteBuilder

  override fun executeDelete(value: T) = delete(value)
    .execute()

  override fun observeDelete(value: T) = delete(value)
    .observe()
}

interface StandardTableDeleteModelCase<T> : TableDeleteModelCase<T> {
  fun deleteTable(): EntityDeleteTableBuilder

  override fun executeTableDelete() = deleteTable()
    .execute()

  override fun observeTableDelete() = deleteTable()
    .observe()
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

  override fun observeBulkDelete(values: Collection<T>) = deleteBuilders
    .bulkDelete(values)
    .observe()

  override fun deleteTable() = deleteBuilders
    .deleteTable()
}
