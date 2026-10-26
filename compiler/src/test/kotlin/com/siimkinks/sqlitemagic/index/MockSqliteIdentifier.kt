package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.internal.SqliteIdentifier

internal fun mockSqliteIdentifier(
  rawName: String = "index_test_table_value"
) = SqliteIdentifier(rawName)
