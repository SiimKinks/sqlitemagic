package com.siimkinks.sqlitemagic.index

fun mockSqliteIdentifier(
  rawName: String = "index_test_table_value"
) = SqliteIdentifier.from(rawName)
