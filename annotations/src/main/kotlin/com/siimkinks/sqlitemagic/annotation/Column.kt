package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines a column in a table.
 *
 * @property value Column name. Defaults to the persisted declaration name with camel case replaced by `_`.
 *
 * @property defaultValue Column default value as SQL. A column is `NOT NULL` when its property and every enclosing
 *     [Embedded] property are non-null. When not defined, nullable columns default to `NULL` and `NOT NULL` columns
 *     default to the SQL type default:
 * - TEXT — `''`
 * - INTEGER — `0`
 * - REAL — `0.0`
 * - BLOB — `X''`, or `X'00'` for `Byte`
 *
 *     A non-null relationship, a [Unique] column and a column of a unique index have no implicit default, so
 *     adding one to an existing table deletes the table's rows in the generated migration.
 *
 * @property handleRecursively Whether a related model should be persisted and retrieved
 *     recursively. In either mode, the referenced type's [Id]-annotated property is stored and used
 *     to identify the related row. When `false`, only that relationship reference is persisted and
 *     retrieved. The referenced type must be defined with [Table] and declare an [Id].
 *
 * @property onDeleteCascade Whether an `ON DELETE CASCADE` foreign-key action should be added to
 *     the table definition for a related model. This option takes effect only when
 *     [handleRecursively] is `true`.
 *
 * @property belongsToIndex Name of the composite index to which this column belongs.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class Column(
  val value: String = "",
  val defaultValue: String = "",
  val handleRecursively: Boolean = true,
  val onDeleteCascade: Boolean = false,
  val belongsToIndex: String = ""
)
