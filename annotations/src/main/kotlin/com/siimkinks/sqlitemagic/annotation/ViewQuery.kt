package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Marks a `SelectQuery` value as the defining query for a [View].
 *
 * Each view must have exactly one query declared directly within the view. In Kotlin, declare it
 * as a non-private companion-object property; `@JvmField` is not required. In Java, declare it as
 * an accessible static field.
 *
 * Values in the query are written into the view definition as SQL literals. The query cannot use named
 * parameters or a connection.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class ViewQuery
