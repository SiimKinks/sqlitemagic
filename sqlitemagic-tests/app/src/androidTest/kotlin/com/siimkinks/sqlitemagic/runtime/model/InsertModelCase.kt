package com.siimkinks.sqlitemagic.runtime.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult

enum class InsertRowIdExpectation {
  PRESENT,
  ABSENT;

  fun assertRowId(rowId: Long?) = when (this) {
    PRESENT -> assertThat(rowId).isNotNull()
    ABSENT -> assertThat(rowId).isNull()
  }
}

interface InsertModelCase<T> : RuntimeModelCase<T> {
  val rowIdExpectation: InsertRowIdExpectation

  fun insert(value: T): EntityInsertBuilder

  fun expectedAfterInsert(value: T, result: EntityInsertResult.Inserted): T

  fun verifyAfterInsert(value: T, result: EntityInsertResult.Inserted) = Unit
}
