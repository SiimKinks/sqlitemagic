package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Defines a full-text index on a table.
 *
 * The index is an external-content SQLite FTS table: it stores the search index, reads the text from the table and
 * is kept in sync by generated triggers. Every [FullText] property of the table that refers to this index is a
 * column of it, in property declaration order.
 *
 * The table must have a stable row ID: an [Id] stored as `INTEGER`, or a [RowId] beside an [Id] stored otherwise.
 * `TEMPORARY` and `WITHOUT_ROWID` tables cannot have a full-text index. A database with a full-text index turns on
 * `PRAGMA recursive_triggers` on each of its connections, so that rows replaced by `CONFLICT_REPLACE` leave the index.
 *
 * Example:
 * ```
 * @Table
 * @FullTextIndex
 * data class Article(
 *   @Id val id: Long? = null,
 *   @FullText val title: String,
 *   @FullText val body: String
 * )
 * ```
 *
 * See [SQLite documentation: FTS5](https://www.sqlite.org/fts5.html) and
 * [SQLite documentation: FTS3 and FTS4](https://www.sqlite.org/fts3.html).
 *
 * @property value FTS table name, which [FullText.index] refers to. Defaults to the table name followed by `_fts`. All
 *     but one of a table's full-text indexes need a name.
 * @property version FTS version. The framework driver supports only FTS4.
 * @property tokenize Tokenizer and its arguments, in the syntax of the version: such as `porter unicode61` or
 *     `unicode61 remove_diacritics 2` for FTS5, and `porter` or `unicode61 "remove_diacritics=2"` for FTS4, which
 *     quotes each argument and cannot wrap `unicode61` in `porter`. Defaults to SQLite's default tokenizer:
 *     `unicode61` for FTS5 and `simple` for FTS4.
 * @property prefix Prefix lengths, in characters, to build prefix indexes for.
 * @property detail How much detail an FTS5 index stores. See [FullTextDetail].
 * @property columnSize Whether an FTS5 index stores the token count of each column of each row.
 * @property matchinfo Which `matchinfo` formats an FTS4 index supports. See [FullTextMatchinfo].
 * @property order Order in which an FTS4 index returns matches. See [FullTextOrder].
 */
@Target(CLASS)
@Retention(BINARY)
@Repeatable
annotation class FullTextIndex(
  val value: String = "",
  val version: FullTextVersion = FullTextVersion.FTS5,
  val tokenize: String = "",
  val prefix: IntArray = [],
  val detail: FullTextDetail = FullTextDetail.FULL,
  val columnSize: Boolean = true,
  val matchinfo: FullTextMatchinfo = FullTextMatchinfo.FTS4,
  val order: FullTextOrder = FullTextOrder.ASC
)
