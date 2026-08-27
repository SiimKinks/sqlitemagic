package com.siimkinks.sqlitemagic.runtime.model

interface UniqueInsertModelCase<T> : InsertModelCase<T> {
  fun conflictingValue(existing: T, sequence: Int): T
}
