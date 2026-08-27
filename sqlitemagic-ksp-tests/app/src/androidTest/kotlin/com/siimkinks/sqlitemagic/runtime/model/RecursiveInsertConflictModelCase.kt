package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table

interface RecursiveInsertConflictModelCase<T> : InsertModelCase<T> {
  val relatedTable: Table<*>

  fun valueWithParentConflict(existing: T, sequence: Int): T

  fun valueWithChildConflict(existing: T, sequence: Int): T
}
