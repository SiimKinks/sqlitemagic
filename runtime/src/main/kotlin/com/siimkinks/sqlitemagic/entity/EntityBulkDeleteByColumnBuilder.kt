package com.siimkinks.sqlitemagic.entity

import com.siimkinks.sqlitemagic.Unique

/**
 * Builder for a bulk delete operation that identifies each entity row by a provided unique column.
 *
 * @param P Parent table type
 */
interface EntityBulkDeleteByColumnBuilder<P> : ConnectionProvidedOperation<EntityBulkDeleteByColumnBuilder<P>> {
  /**
   * Execute this configured bulk delete operation against a database using the provided column
   * to identify each entity row.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return Nr of deleted records
   */
  fun execute(byColumn: Unique<P>): Int
}
