package com.siimkinks.sqlitemagic.model

fun mockIndexElement(
  name: String = "index_test_table_value",
  isUnique: Boolean = false
) = IndexElement(
  name = name,
  isUnique = isUnique
)
