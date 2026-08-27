package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveInsertConflictModelCase<T> : RecursiveBulkInsertModelCase<T> {
  fun valueWithParentConflict(existing: T, sequence: Int): T

  fun valueWithChildConflict(existing: T, sequence: Int): T
}
