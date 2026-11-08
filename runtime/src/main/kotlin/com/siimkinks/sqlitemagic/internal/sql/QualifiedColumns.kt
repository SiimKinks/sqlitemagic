package com.siimkinks.sqlitemagic.internal.sql

internal class QualifiedColumns(
  val qualifier: String,
  quotedNames: List<String>
) {
  val sql = buildString {
    quotedNames.forEachIndexed { index, quotedName ->
      if (index != 0) {
        append(", ")
      }
      if (qualifier.isNotEmpty()) {
        append(qualifier)
        append('.')
      }
      append(quotedName)
    }
  }
}
