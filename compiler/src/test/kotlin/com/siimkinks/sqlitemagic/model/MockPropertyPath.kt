package com.siimkinks.sqlitemagic.model

internal fun mockPropertyPath(
  vararg segments: String
) = PropertyPath(
  segments = segments
    .toList()
    .ifEmpty { listOf("value") }
)
