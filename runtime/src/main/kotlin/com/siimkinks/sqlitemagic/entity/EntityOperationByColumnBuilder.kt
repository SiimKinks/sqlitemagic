package com.siimkinks.sqlitemagic.entity

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Unique

interface EntityOperationByColumnBuilder<R> {
  /**
   * Configure this operation to be executed by the provided column.
   * Executed operation will be performed using `WHERE column=?` clause.
   *
   * @param column Generated non-null unique column of the table for this operation
   * @return Operation builder
   * @throws IllegalArgumentException if [column] is not a column of the table for this operation
   */
  @CheckResult
  fun byColumn(column: Unique<*>): R
}
