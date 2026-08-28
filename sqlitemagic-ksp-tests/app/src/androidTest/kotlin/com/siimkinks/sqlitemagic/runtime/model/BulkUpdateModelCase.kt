package com.siimkinks.sqlitemagic.runtime.model

import io.reactivex.Completable

interface BulkUpdateModelCase<T> : UpdateModelCase<T> {
  fun executeBulkUpdate(
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): Boolean

  fun observeBulkUpdate(
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): Completable
}
