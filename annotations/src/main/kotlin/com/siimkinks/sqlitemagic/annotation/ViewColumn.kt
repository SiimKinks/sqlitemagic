package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines [View] column details.
 *
 * @property value Fully qualified column name or column alias, such as `author.name` or `foo`.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class ViewColumn(
  val value: String
)
