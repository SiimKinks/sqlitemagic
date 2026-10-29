package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.ReactiveCompletion

interface BulkPersistModelCase<T> : PersistModelCase<T>, BulkUpdateModelCase<T>, BulkInsertModelCase<T> {
  fun executeBulkPersist(
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): Boolean

  fun observeBulkPersist(
    adapter: ReactiveAdapter,
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): ReactiveCompletion
}

interface RepresentativeEmptyBulkModelCase<T> : BulkPersistModelCase<T>
