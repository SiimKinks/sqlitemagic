package com.siimkinks.sqlitemagic.annotation

/** SQLite full-text search version of a [FullTextIndex]. */
enum class FullTextVersion {
  /** FTS5, with ranking, `bm25`, `highlight` and `snippet`. The framework driver does not support it. */
  FTS5,

  /** FTS4, which both the framework and the bundled driver support. */
  FTS4
}
