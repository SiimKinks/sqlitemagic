package com.siimkinks.sqlitemagic.entity

/**
 * Builder for delete operation.
 */
interface EntityDeleteBuilder : ConnectionProvidedOperation<EntityDeleteBuilder> {
  /**
   * Execute this configured delete operation against a database.
   *
   * @return Nr of deleted records
   */
  fun execute(): Int
}
