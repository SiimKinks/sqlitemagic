package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveInsertConflictModelCase<T> : RecursiveBulkInsertModelCase<T> {
  fun valueWithParentConflict(existing: T, sequence: Int): T

  fun valueWithChildConflict(existing: T, sequence: Int): T

  fun valueWithInsertConflict(
    existing: T,
    conflict: RecursiveConflictTarget,
    sequence: Int
  ) = when (conflict) {
    RecursiveConflictTarget.PARENT -> valueWithParentConflict(
      existing = existing,
      sequence = sequence
    )
    RecursiveConflictTarget.CHILD -> valueWithChildConflict(
      existing = existing,
      sequence = sequence
    )
  }
}
