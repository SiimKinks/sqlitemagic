package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import io.reactivex.Completable
import io.reactivex.Single

interface NullOmittingPersistConflictModelCase<T> : PersistConflictModelCase<T> {
  fun withNullOmittingValues(value: T): T

  fun valueWithNullOmittingInsertConflict(
    existing: T,
    sequence: Int
  ) = withNullOmittingValues(
    value = valueWithInsertConflict(
      existing = existing,
      sequence = sequence
    )
  )

  fun valueWithNullOmittingUpdateConflict(
    existing: T,
    conflicting: T,
    sequence: Int
  ) = withNullOmittingValues(
    value = valueWithUpdateConflict(
      existing = existing,
      conflicting = conflicting,
      sequence = sequence
    )
  )
}

interface NullOmittingPersistModelCase<T> : BulkPersistModelCase<T> {
  fun partialNullValue(sequence: Int): T

  fun partialNullUpdatedValue(
    value: T,
    sequence: Int
  ): T

  fun expectedAfterNullOmittingUpdate(
    existing: T,
    value: T
  ): T

  val relatedTableForNullOmittingPersist: Table<*>?
    get() = null

  fun expectedRelatedValues(values: List<T>): List<*>? = null

  fun executePersistIgnoringNullValues(value: T): EntityPersistResult

  fun observePersistIgnoringNullValues(value: T): Single<EntityPersistResult>

  fun executeBulkPersistIgnoringNullValues(values: Iterable<T>): Boolean

  fun observeBulkPersistIgnoringNullValues(values: Iterable<T>): Completable
}

interface StandardNullOmittingPersistModelCase<T> :
  NullOmittingPersistModelCase<T>,
  StandardBulkPersistModelCase<T> {
  override fun executePersistIgnoringNullValues(value: T) = persist(value = value)
    .ignoreNullValues()
    .execute()

  override fun observePersistIgnoringNullValues(value: T) = persist(value = value)
    .ignoreNullValues()
    .observe()

  override fun executeBulkPersistIgnoringNullValues(values: Iterable<T>) = bulkPersist(values = values)
    .ignoreNullValues()
    .execute()

  override fun observeBulkPersistIgnoringNullValues(values: Iterable<T>) = bulkPersist(values = values)
    .ignoreNullValues()
    .observe()
}

interface NullOmittingAllNullPersistModelCase<T> : NullOmittingPersistModelCase<T> {
  fun allNullValueForMissingRow(): T

  fun allNullValueForExistingRow(value: T): T

  fun expectedAfterAllNullBulkInsert(actual: List<T>): List<T>
}

interface RecursiveNullOmittingPersistConflictModelCase<T> :
  NullOmittingPersistConflictModelCase<T>,
  RecursivePersistUpdateConflictModelCase<T> {
  fun valueWithNullOmittingInsertConflict(
    existing: T,
    conflict: RecursiveConflictTarget,
    sequence: Int
  ) = withNullOmittingValues(
    value = valueWithInsertConflict(
      existing = existing,
      conflict = conflict,
      sequence = sequence
    )
  )

  fun valueWithNullOmittingUpdateConflict(
    existing: T,
    conflicting: T,
    conflict: RecursiveConflictTarget,
    sequence: Int
  ) = withNullOmittingValues(
    value = valueWithUpdateConflict(
      existing = existing,
      conflicting = conflicting,
      conflict = conflict,
      sequence = sequence
    )
  )
}

interface RecursiveNullOmittingPersistModelCase<T> :
  NullOmittingPersistModelCase<T>,
  RecursiveModelCase<T> {
  override val relatedTableForNullOmittingPersist get() = relatedTable

  override fun expectedRelatedValues(values: List<T>) = values.flatMap(::relatedValues)
}
