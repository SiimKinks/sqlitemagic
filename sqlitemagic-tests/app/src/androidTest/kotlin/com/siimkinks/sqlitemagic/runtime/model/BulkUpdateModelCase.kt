package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.ReactiveCompletion

interface BulkUpdateModelCase<T> : UpdateModelCase<T> {
  fun executeBulkUpdate(
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): Boolean

  fun observeBulkUpdate(
    adapter: ReactiveAdapter,
    values: Iterable<T>,
    conflictAlgorithm: Int? = null
  ): ReactiveCompletion
}
