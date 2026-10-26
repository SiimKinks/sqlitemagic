package com.siimkinks.sqlitemagic.entity

import com.siimkinks.sqlitemagic.exception.OperationFailedException

/**
 * Outcome of persisting a single entity.
 *
 * A persist operation first attempts to update an existing row. If no row is updated, it inserts a new one.
 * The concrete result indicates whether a row was inserted, updated, or left unchanged because the configured
 * conflict algorithm ignored a conflict.
 *
 * Entity IDs are not included in the result because they may be absent or use a type other than
 * SQLite's row ID. The [Inserted] result exposes SQLite's generated row ID when the table has one.
 *
 * SQLite's errors are thrown as they are. A write that SQLite completes without writing a row and without an
 * ignoring conflict algorithm is reported with [OperationFailedException].
 */
sealed interface EntityPersistResult {
  /**
   * A new row was inserted.
   *
   * [rowId] identifies the SQLite row and may differ from the entity's declared ID.
   *
   * @property rowId Row ID that SQLite assigned to the new row, or `null` for a `WITHOUT ROWID` table
   */
  data class Inserted(val rowId: Long?) : EntityPersistResult

  /**
   * An existing row was updated.
   *
   * The result carries no additional value. Any declared entity ID remains available from the entity itself.
   */
  data object Updated : EntityPersistResult

  /**
   * No row was inserted or updated because the configured conflict algorithm ignored a conflict.
   */
  data object Ignored : EntityPersistResult
}
