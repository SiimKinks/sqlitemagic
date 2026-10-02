package com.siimkinks.sqlitemagic.runtime.model

interface RecursiveUpdateConflictModelCase<T> : BulkUpdateConflictModelCase<T>, RecursiveBulkUpdateModelCase<T> {
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
