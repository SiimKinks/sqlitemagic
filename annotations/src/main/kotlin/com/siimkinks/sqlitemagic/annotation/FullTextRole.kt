package com.siimkinks.sqlitemagic.annotation

/** Role of a [FullText] column in its [FullTextIndex]. */
enum class FullTextRole {
  /** The column is searched. */
  INDEXED,

  /** The column is part of the FTS table and readable through it, but not searched. */
  UNINDEXED,

  /**
   * The column holds the language ID of the row in an FTS4 index, which splits the index by its value. SQLite reads
   * the value as an integer, and an index has at most one.
   */
  LANGUAGE_ID
}
