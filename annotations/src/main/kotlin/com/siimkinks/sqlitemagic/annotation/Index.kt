package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines an INDEX on a table.
 *
 * Adding an index usually speeds up select queries but slows down other queries, such as inserts
 * and updates. Add indices only when this additional cost is worth the gain.
 *
 * An index can be defined on a column to create an index for that column, or on a table to create
 * a composite index. Every column in a composite index must have a [Column] annotation whose
 * [Column.belongsToIndex] property references the composite index name.
 *
 * See [SQLite documentation: CREATE INDEX](https://www.sqlite.org/lang_createindex.html).
 *
 * @property value Index name. Defaults to the indexed column names joined by `_` and prefixed by
 *     `index_<table name>`. For example, columns `bar` and `baz` on table `foo` produce
 *     `index_foo_bar_baz`.
 *
 * @property unique Whether duplicate indexed values should be rejected.
 */
@Target(CLASS, FIELD)
@Retention(BINARY)
annotation class Index(
  val value: String = "",
  val unique: Boolean = false
)
