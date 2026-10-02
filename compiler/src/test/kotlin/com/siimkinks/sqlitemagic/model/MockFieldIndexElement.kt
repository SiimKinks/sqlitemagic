package com.siimkinks.sqlitemagic.model

fun mockFieldIndexElement(
  name: String = "index_test_table_value",
  isUnique: Boolean = false
) = FieldIndexElement(
  name = name,
  isUnique = isUnique
)
