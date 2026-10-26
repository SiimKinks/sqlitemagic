package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.SqlExpression

data class QueryPredicateCase(
  val name: String,
  val predicate: () -> SqlExpression<Boolean, *, *>,
  val expectedValues: List<String>
) {
  override fun toString() = name
}
