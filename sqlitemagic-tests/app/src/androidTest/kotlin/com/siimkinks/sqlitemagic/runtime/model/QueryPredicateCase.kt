package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Expr

data class QueryPredicateCase(
  val name: String,
  val predicate: () -> Expr,
  val expectedValues: List<String>
) {
  override fun toString() = name
}
