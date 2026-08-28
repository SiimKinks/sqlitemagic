package com.siimkinks.sqlitemagic.runtime.model

import io.reactivex.Completable

interface BulkPersistModelCase<T> : PersistModelCase<T>, BulkUpdateModelCase<T>, BulkInsertModelCase<T> {
  fun executeBulkPersist(
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): Boolean

  fun observeBulkPersist(
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): Completable
}
