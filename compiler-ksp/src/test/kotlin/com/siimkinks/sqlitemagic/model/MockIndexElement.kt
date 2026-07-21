package com.siimkinks.sqlitemagic.model

fun mockIndexElement(
  name: String = "index_test_table_value",
  isUnique: Boolean = false,
  belongsToIndex: String? = null
) = IndexElement(
  name = name,
  isUnique = isUnique,
  belongsToIndex = belongsToIndex
)
