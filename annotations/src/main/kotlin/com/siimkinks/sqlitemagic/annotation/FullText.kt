package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Adds a column to a [FullTextIndex] of its table.
 *
 * Any persisted column can be indexed, including columns of [Embedded] values. The index stores the value of the
 * column as SQLite stores it, so a transformed column is indexed in its stored form. A property marked only with
 * [FullText] is persisted also when [Table.persistAll] is false. A property can be a column of several indexes.
 *
 * In an [Embedded] value, the column joins the index only in tables that declare it, so one embedded type can serve
 * tables with and without that index. On a property of the table itself, the index must be declared.
 *
 * @property index Name of the [FullTextIndex] the column belongs to. Defaults to the index without a name.
 * @property role Role of the column in the index. See [FullTextRole].
 */
@Target(FIELD)
@Retention(BINARY)
@Repeatable
annotation class FullText(
  val index: String = "",
  val role: FullTextRole = FullTextRole.INDEXED
)
