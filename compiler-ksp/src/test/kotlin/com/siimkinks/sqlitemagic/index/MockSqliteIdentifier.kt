package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.internal.SqliteIdentifier

fun mockSqliteIdentifier(
  rawName: String = "index_test_table_value"
) = SqliteIdentifier.from(rawName)
