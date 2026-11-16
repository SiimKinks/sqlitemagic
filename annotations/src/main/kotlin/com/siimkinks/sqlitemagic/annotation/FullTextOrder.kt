package com.siimkinks.sqlitemagic.annotation

/** Order in which an FTS4 [FullTextIndex] returns matches. */
enum class FullTextOrder {
  /** Ascending row ID order. */
  ASC,

  /** Descending row ID order, which makes ordering by row ID descending cheap. */
  DESC
}
