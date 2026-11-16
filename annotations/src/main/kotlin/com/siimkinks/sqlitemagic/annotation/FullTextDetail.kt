package com.siimkinks.sqlitemagic.annotation

/** How much detail an FTS5 [FullTextIndex] stores. */
enum class FullTextDetail {
  /** Stores the column and position of each token. */
  FULL,

  /**
   * Stores the column of each token but not its position, which makes the index smaller. Phrase and `NEAR` queries
   * fail.
   */
  COLUMN,

  /** Stores neither the column nor the position of a token. Phrase, `NEAR` and column filter queries fail. */
  NONE
}
