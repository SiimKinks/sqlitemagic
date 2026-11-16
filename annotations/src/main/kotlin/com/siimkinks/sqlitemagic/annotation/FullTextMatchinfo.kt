package com.siimkinks.sqlitemagic.annotation

/** Which `matchinfo` formats an FTS4 [FullTextIndex] supports. */
enum class FullTextMatchinfo {
  /** Every format. */
  FTS4,

  /** Only the formats FTS3 supports, which makes the index smaller. */
  FTS3
}
