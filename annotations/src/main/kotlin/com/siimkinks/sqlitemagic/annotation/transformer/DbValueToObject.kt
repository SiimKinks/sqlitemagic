package com.siimkinks.sqlitemagic.annotation.transformer

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Marks a function that transforms a raw value retrieved from the database into an object.
 *
 * For each transformed type, exactly one [ObjectToDbValue] function and one matching
 * [DbValueToObject] function must be defined. This function must have exactly one parameter
 * containing a supported SQLite storage type and return the transformed object. The paired
 * [ObjectToDbValue] function must accept that transformed type and return the serialized type.
 * The transformed type must not itself be a supported SQLite storage type or a type annotated
 * with `@Table`.
 *
 * Kotlin transformer functions may be top-level or declared in an object or companion object.
 * Companion-object functions do not require `@JvmStatic`. Java transformer methods must be
 * static. Transformer functions must not be private, suspending, or extension functions.
 */
@Target(FUNCTION)
@Retention(BINARY)
annotation class DbValueToObject
