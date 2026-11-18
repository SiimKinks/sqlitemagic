package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines a column as the row ID of a table whose [Id] is not stored as `INTEGER`.
 *
 * The property must be `Long?`. Its column is the table's `INTEGER PRIMARY KEY`, and the [Id] column becomes
 * `UNIQUE`; the [Id] stays the entity key for lookups, relationships and entity operations. SQLite assigns the row
 * ID when a row is inserted: entity operations never write it, the insert result's `rowId` returns it, and a `var`
 * property is assigned after insert. The row ID of a row that `CONFLICT_REPLACE` replaces changes.
 *
 * A stable row ID is what full-text indexes and other row-ID-keyed data refer to: a `VACUUM` or a dump and restore
 * can renumber the implicit row IDs of a table without one.
 *
 * A table declares at most one row ID. It cannot be declared in `WITHOUT_ROWID` or `TEMPORARY` tables or inside an
 * [Embedded] value, and cannot be combined on the same property with [Id], [Unique], [Index], [IgnoreColumn],
 * [Column.defaultValue], or [Column.belongsToIndex].
 *
 * Example:
 * ```
 * @Table
 * data class Note(
 *   @Id val id: String,
 *   @RowId val rowId: Long? = null,
 *   val title: String
 * )
 * ```
 *
 * See [SQLite documentation: Rowid Tables](https://www.sqlite.org/rowidtable.html).
 */
@Target(FIELD)
@Retention(BINARY)
annotation class RowId
