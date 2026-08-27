package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table

interface RecursiveBulkInsertModelCase<T> : BulkInsertModelCase<T> {
  val relatedTable: Table<*>

  fun relatedValues(value: T): List<*>
}
