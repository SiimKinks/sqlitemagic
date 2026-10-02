package com.siimkinks.sqlitemagic.runtime.model

import io.reactivex.Completable

interface UpdateModelCase<T> : InsertModelCase<T> {
  fun updatedValue(value: T, sequence: Int): T

  fun executeUpdate(
    value: T,
    conflictAlgorithm: Int? = null
  ): Boolean

  fun observeUpdate(
    value: T,
    conflictAlgorithm: Int? = null
  ): Completable

  fun expectedAfterUpdate(value: T): T = value
}
