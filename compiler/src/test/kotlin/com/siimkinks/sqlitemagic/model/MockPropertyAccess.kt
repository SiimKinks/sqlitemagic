package com.siimkinks.sqlitemagic.model

fun mockPropertyAccess(
  path: PropertyPath = mockPropertyPath(),
  isMutable: Boolean = false
) = PropertyAccess(
  path = path,
  isMutable = isMutable
)
