package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult

enum class InsertRowIdExpectation {
  PRESENT,
  ABSENT
}

interface InsertModelCase<T> : RuntimeModelCase<T> {
  val rowIdExpectation: InsertRowIdExpectation

  fun insert(value: T): EntityInsertBuilder

  fun expectedAfterInsert(value: T, result: EntityInsertResult.Inserted): T

  fun verifyAfterInsert(value: T, result: EntityInsertResult.Inserted) = Unit
}
