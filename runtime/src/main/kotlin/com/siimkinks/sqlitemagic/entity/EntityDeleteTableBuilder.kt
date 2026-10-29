package com.siimkinks.sqlitemagic.entity

/**
 * Builder for table delete operation.
 */
interface EntityDeleteTableBuilder : ConnectionProvidedOperation<EntityDeleteTableBuilder> {
  /**
   * Execute this configured table delete operation against a database.
   *
   * @return Nr of deleted records
   */
  fun execute(): Int
}
