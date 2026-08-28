package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder

interface BulkUpdateConflictModelCase<T> : UpdateConflictModelCase<T> {
  fun updatedValue(value: T, sequence: Int): T

  fun bulkUpdate(values: List<T>): EntityBulkUpdateBuilder
}
