package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines a UNIQUE constraint on a column.
 *
 * A table may have any number of unique columns. Each non-`NULL` value in the annotated column
 * must be distinct from the values in every other row. `NULL` values are considered distinct from
 * every other value, including other `NULL` values. Use [Index] with [Index.unique] for a composite
 * unique index.
 *
 * On a table without an [Id], a non-null unique column may be supplied explicitly as the key for
 * an identity-dependent entity operation. The generated unique-column property must be passed to
 * that operation's `execute` or `observe` terminal. Nullable unique columns cannot serve as entity
 * operation keys.
 *
 * See [SQLite documentation: CREATE TABLE](https://www.sqlite.org/lang_createtable.html).
 */
@Target(FIELD)
@Retention(BINARY)
annotation class Unique
