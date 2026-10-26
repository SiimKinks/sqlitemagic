package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines [View] column details.
 *
 * @property value Fully qualified column name or column alias, such as `author.name` or `foo`. For a property whose
 *     type is a [Table] or [View] model, the table name or alias that prefixes its columns, such as `author`.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class ViewColumn(
  val value: String
)
