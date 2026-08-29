package com.siimkinks.sqlitemagic.runtime.model

interface RecursivePersistConflictModelCase<T> :
  PersistConflictModelCase<T>,
  RecursiveInsertConflictModelCase<T> {
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

interface RecursivePersistUpdateConflictModelCase<T> :
  RecursivePersistConflictModelCase<T>,
  RecursiveUpdateConflictModelCase<T> {
  fun valueWithUpdateConflict(
    existing: T,
    conflicting: T,
    conflict: RecursiveConflictTarget,
    sequence: Int
  ) = when (conflict) {
    RecursiveConflictTarget.PARENT -> valueWithParentConflict(
      existing = existing,
      conflicting = conflicting,
      sequence = sequence
    )
    RecursiveConflictTarget.CHILD -> valueWithChildConflict(
      existing = existing,
      conflicting = conflicting,
      sequence = sequence
    )
  }
}
