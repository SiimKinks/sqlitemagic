package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder

interface UpdateConflictModelCase<T> : InsertModelCase<T> {
  fun update(value: T): EntityUpdateBuilder

  fun valueWithConflict(
    existing: T,
    conflicting: T,
    sequence: Int
  ): T
}
