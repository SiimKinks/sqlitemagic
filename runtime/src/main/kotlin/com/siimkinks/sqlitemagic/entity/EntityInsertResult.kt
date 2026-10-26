package com.siimkinks.sqlitemagic.entity

import com.siimkinks.sqlitemagic.exception.OperationFailedException

/**
 * Outcome of inserting a single entity.
 *
 * The concrete result indicates whether a row was inserted or left unchanged because the configured conflict algorithm
 * ignored a conflict.
 *
 * Entity IDs are not included in the result because they may be absent or use a type other than SQLite's row ID.
 * Callers can read a declared entity ID from the entity itself. The [Inserted] result exposes SQLite's generated
 * row ID when the table has one.
 *
 * SQLite's errors are thrown as they are. An insert that SQLite completes without writing a row and without an
 * ignoring conflict algorithm is reported with [OperationFailedException].
 */
sealed interface EntityInsertResult {
  /**
   * A new row was inserted.
   *
   * [rowId] identifies the SQLite row and may differ from the entity's declared ID.
   *
   * @property rowId Row ID that SQLite assigned to the new row, or `null` for a `WITHOUT ROWID` table
   */
  data class Inserted(val rowId: Long?) : EntityInsertResult

  /**
   * No row was inserted because the configured conflict algorithm ignored a conflict.
   */
  data object Ignored : EntityInsertResult
}
