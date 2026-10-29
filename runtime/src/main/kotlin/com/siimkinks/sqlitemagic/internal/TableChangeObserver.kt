package com.siimkinks.sqlitemagic.internal

/**
 * Receives the table changes of a connection. Register it with [QueryObservation.register].
 *
 * Calls arrive on the writing thread after the change is applied, outside any library lock. They may arrive
 * concurrently from several writing threads, after [onClosed], and after the registration was closed.
 */
@InternalSqliteMagicApi
interface TableChangeObserver {
  /**
   * Handle a change of tables.
   *
   * @param tables Names of the changed tables, which may include tables that the registration does not observe
   */
  fun onTablesChanged(tables: Set<String>)

  /** Handle closing of the connection, after which it publishes no further changes. */
  fun onClosed()
}
