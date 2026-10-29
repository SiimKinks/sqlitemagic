package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.ReactiveCompletion

interface UpdateModelCase<T> : InsertModelCase<T> {
  fun updatedValue(value: T, sequence: Int): T

  fun executeUpdate(
    value: T,
    conflictAlgorithm: Int? = null
  ): Boolean

  fun observeUpdate(
    adapter: ReactiveAdapter,
    value: T,
    conflictAlgorithm: Int? = null
  ): ReactiveCompletion

  fun expectedAfterUpdate(value: T): T = value
}
