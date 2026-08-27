package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder

interface BulkInsertModelCase<T> : InsertModelCase<T> {
  fun bulkInsert(values: List<T>): EntityBulkInsertBuilder

  fun expectedAfterBulkInsert(
    values: List<T>,
    actual: List<T>
  ): List<T> = values
}
