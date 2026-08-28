package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveUpdateConflictModelCase<T> : UpdateConflictModelCase<T>, RecursiveBulkInsertModelCase<T> {
  fun valueWithParentConflict(
    existing: T,
    conflicting: T,
    sequence: Int
  ): T

  fun valueWithChildConflict(
    existing: T,
    conflicting: T,
    sequence: Int
  ): T
}
