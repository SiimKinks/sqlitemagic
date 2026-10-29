package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm

data class StandardOperationBuilders<T>(
  val insert: (T) -> EntityInsertBuilder,
  val bulkInsert: (List<T>) -> EntityBulkInsertBuilder,
  val update: (T) -> EntityUpdateBuilder,
  val bulkUpdate: (Iterable<T>) -> EntityBulkUpdateBuilder,
  val persist: (T) -> EntityPersistBuilder,
  val bulkPersist: (Iterable<T>) -> EntityBulkPersistBuilder
)

interface StandardUpdateModelCase<T> : UpdateModelCase<T> {
  fun update(value: T): EntityUpdateBuilder

  override fun executeUpdate(
    value: T,
    conflictAlgorithm: Int?
  ) = update(value)
    .withConflictAlgorithm(conflictAlgorithm)
    .execute()

  override fun observeUpdate(
    adapter: ReactiveAdapter,
    value: T,
    conflictAlgorithm: Int?
  ) = adapter.observe(
    update(value)
      .withConflictAlgorithm(conflictAlgorithm)
  )
}

interface StandardBulkUpdateModelCase<T> : BulkUpdateModelCase<T>, StandardUpdateModelCase<T> {
  fun bulkUpdate(values: Iterable<T>): EntityBulkUpdateBuilder

  override fun executeBulkUpdate(
    values: Iterable<T>,
    conflictAlgorithm: Int?
  ) = bulkUpdate(values)
    .withConflictAlgorithm(conflictAlgorithm)
    .execute()

  override fun observeBulkUpdate(
    adapter: ReactiveAdapter,
    values: Iterable<T>,
    conflictAlgorithm: Int?
  ) = adapter.observe(
    bulkUpdate(values)
      .withConflictAlgorithm(conflictAlgorithm)
  )
}

interface StandardPersistModelCase<T> : PersistModelCase<T>, StandardUpdateModelCase<T> {
  fun persist(value: T): EntityPersistBuilder

  override fun executePersist(value: T) = persist(value)
    .execute()

  override fun observePersist(
    adapter: ReactiveAdapter,
    value: T
  ) = adapter.observe(persist(value))
}

interface StandardBulkPersistModelCase<T> :
  BulkPersistModelCase<T>,
  StandardPersistModelCase<T>,
  StandardBulkUpdateModelCase<T> {
  val operationBuilders: StandardOperationBuilders<T>

  override fun insert(value: T): EntityInsertBuilder = operationBuilders.insert(value)

  override fun bulkInsert(values: List<T>): EntityBulkInsertBuilder = operationBuilders.bulkInsert(values)

  override fun update(value: T): EntityUpdateBuilder = operationBuilders.update(value)

  override fun bulkUpdate(values: Iterable<T>): EntityBulkUpdateBuilder = operationBuilders.bulkUpdate(values)

  override fun persist(value: T): EntityPersistBuilder = operationBuilders.persist(value)

  fun bulkPersist(values: Iterable<T>): EntityBulkPersistBuilder = operationBuilders.bulkPersist(values)

  override fun executeBulkPersist(
    values: Iterable<T>,
    conflictAlgorithm: Int?
  ) = bulkPersist(values)
    .withConflictAlgorithm(conflictAlgorithm)
    .execute()

  override fun observeBulkPersist(
    adapter: ReactiveAdapter,
    values: Iterable<T>,
    conflictAlgorithm: Int?
  ) = adapter.observe(
    bulkPersist(values)
      .withConflictAlgorithm(conflictAlgorithm)
  )
}
