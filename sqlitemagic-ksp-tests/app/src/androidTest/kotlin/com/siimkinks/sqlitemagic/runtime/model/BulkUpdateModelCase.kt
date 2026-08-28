package com.siimkinks.sqlitemagic.runtime.model

import io.reactivex.Completable

interface BulkUpdateModelCase<T> : UpdateModelCase<T> {
  fun executeBulkUpdate(values: List<T>): Boolean

  fun observeBulkUpdate(values: List<T>): Completable
}
