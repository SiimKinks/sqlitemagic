package com.siimkinks.sqlitemagic.entity

/**
 * Builder for bulk delete operation.
 */
interface EntityBulkDeleteBuilder : ConnectionProvidedOperation<EntityBulkDeleteBuilder> {
  /**
   * Execute this configured bulk delete operation against a database.
   *
   * @return Nr of deleted records
   */
  fun execute(): Int
}
