package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Marks a `CompiledSelect` value as the defining query for a [View].
 *
 * Each view must have exactly one query declared directly within the view. In Kotlin, declare it
 * as a non-private companion-object property; `@JvmField` is not required. In Java, declare it as
 * an accessible static field.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class ViewQuery
