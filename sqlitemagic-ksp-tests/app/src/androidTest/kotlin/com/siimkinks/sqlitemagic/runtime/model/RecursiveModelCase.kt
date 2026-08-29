package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table

interface RecursiveModelCase<T> : RuntimeModelCase<T> {
  val relatedTable: Table<*>

  fun relatedValues(value: T): List<*>
}
