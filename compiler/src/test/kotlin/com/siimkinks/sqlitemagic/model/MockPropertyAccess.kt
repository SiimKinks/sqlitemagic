package com.siimkinks.sqlitemagic.model

internal fun mockPropertyAccess(
  path: PropertyPath = mockPropertyPath()
) = PropertyAccess(
  path = path,
  isMutable = false
)
