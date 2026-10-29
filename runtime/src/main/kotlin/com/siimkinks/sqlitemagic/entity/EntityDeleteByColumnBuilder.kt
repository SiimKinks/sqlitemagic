package com.siimkinks.sqlitemagic.entity

import com.siimkinks.sqlitemagic.Unique

/**
 * Builder for a delete operation that identifies an entity row by a provided unique column.
 *
 * @param P Parent table type
 */
interface EntityDeleteByColumnBuilder<P> : ConnectionProvidedOperation<EntityDeleteByColumnBuilder<P>> {
  /**
   * Execute this configured delete operation against a database using the provided column to identify the entity row.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return Nr of deleted records
   */
  fun execute(byColumn: Unique<P>): Int
}
