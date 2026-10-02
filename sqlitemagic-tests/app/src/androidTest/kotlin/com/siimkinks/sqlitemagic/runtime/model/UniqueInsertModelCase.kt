package com.siimkinks.sqlitemagic.runtime.model

interface UniqueInsertModelCase<T> : BulkInsertModelCase<T> {
  fun conflictingValue(existing: T, sequence: Int): T
}
