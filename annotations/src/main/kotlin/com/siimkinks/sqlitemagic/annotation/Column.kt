package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines a column in a table.
 *
 * @property value Column name. Defaults to the persisted declaration name with camel case replaced by `_`.
 *
 * @property defaultValue Column default value. When not defined, defaults to the SQL type default:
 * - TEXT — `""`
 * - INTEGER — `0`
 * - REAL — `0.0`
 * - BLOB — `0`
 * - Nullable columns default to `NULL`.
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
