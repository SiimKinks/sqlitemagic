package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Excludes a property from persistence when [Table.persistAll] is `true`.
 *
 * This annotation has no other functionality.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class IgnoreColumn
