package com.siimkinks.sqlitemagic.entity

import android.database.SQLException
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Ignored
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Inserted
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Updated
import com.siimkinks.sqlitemagic.exception.OperationFailedException

/**
 * Builder for a persist operation that identifies an existing entity row by a provided unique column.
 *
 * Persist is an operation that first tries to update and if that fails then inserts the provided entity.
 *
 * @param P Parent table type
 */
interface EntityPersistByColumnBuilder<P> : EntityPersistOperationBuilder<EntityPersistByColumnBuilder<P>> {
  /**
   * Execute this configured persist operation against a database using the provided column
   * to identify the entity row. Operation will be executed inside a transaction if the persisted
   * entity has complex columns which also need to be persisted.
   *
   * Returns [Inserted] when a new row was inserted, [Updated] when an existing row was updated, or
   * [Ignored] when the configured conflict algorithm intentionally ignored the write.
   *
   * @param byColumn Generated non-null unique column of the table for this operation
   * @return The persist operation result
   * @throws SQLException when SQLite rejects the write, for example on a constraint violation
   * @throws OperationFailedException when SQLite writes no row without an error and the conflict algorithm does not
   *     ignore conflicts
   */
  fun execute(byColumn: Unique<P>): EntityPersistResult
}
