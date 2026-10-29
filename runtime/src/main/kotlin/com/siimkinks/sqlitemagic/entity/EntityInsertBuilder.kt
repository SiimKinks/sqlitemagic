package com.siimkinks.sqlitemagic.entity

import android.database.SQLException
import com.siimkinks.sqlitemagic.entity.EntityInsertResult.Ignored
import com.siimkinks.sqlitemagic.entity.EntityInsertResult.Inserted
import com.siimkinks.sqlitemagic.exception.OperationFailedException

/**
 * Builder for insert operation.
 */
interface EntityInsertBuilder : EntityOperationBuilder<EntityInsertBuilder> {
  /**
   * Execute this configured insert operation against a database. Operation will be executed inside
   * a transaction if the inserted entity has complex columns which also need to be inserted.
   *
   * @return [Inserted] when a row was inserted or [Ignored] when a conflict was intentionally ignored
   * @throws SQLException when SQLite rejects the insert, for example on a constraint violation
   * @throws OperationFailedException when SQLite writes no row without an error and the conflict algorithm does not
   *     ignore conflicts
   */
  fun execute(): EntityInsertResult
}
