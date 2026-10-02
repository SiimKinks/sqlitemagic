package com.siimkinks.sqlitemagic.model

fun mockPropertyPath(
  vararg segments: String
) = PropertyPath(
  segments = segments
    .toList()
    .ifEmpty { listOf("value") }
)
