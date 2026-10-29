package com.siimkinks.sqlitemagic.entity

import android.database.SQLException

/**
 * Builder for update operation.
 */
interface EntityUpdateBuilder :
  EntityOperationBuilder<EntityUpdateBuilder>,
  EntityOperationByColumnBuilder<EntityUpdateBuilder> {
  /**
   * Execute this configured update operation against a database. Operation will be executed inside
   * a transaction if the updated entity has complex columns which also need to be updated.
   *
   * @return `true` if the operation was successful; `false` when no row was updated and the operation was rolled
   *     back
   * @throws SQLException when SQLite rejects the update, for example on a constraint violation
   */
  fun execute(): Boolean
}
