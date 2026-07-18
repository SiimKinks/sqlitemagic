package com.siimkinks.sqlitemagic.annotation

/** SQLite options applied to a [Table] definition. */
enum class TableOption {
  /** Creates the table in the temporary database for the current database connection. */
  TEMPORARY,

  /** Uses the declared primary key as the table storage key instead of a separate row ID. */
  WITHOUT_ROWID
}
