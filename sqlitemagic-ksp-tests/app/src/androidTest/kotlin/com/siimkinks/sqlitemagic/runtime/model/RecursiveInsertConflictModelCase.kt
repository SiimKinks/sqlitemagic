package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table

interface RecursiveInsertConflictModelCase<T> : BulkInsertModelCase<T> {
  val relatedTable: Table<*>

  fun relatedValues(value: T): List<*>

  fun valueWithParentConflict(existing: T, sequence: Int): T

  fun valueWithChildConflict(existing: T, sequence: Int): T
}
