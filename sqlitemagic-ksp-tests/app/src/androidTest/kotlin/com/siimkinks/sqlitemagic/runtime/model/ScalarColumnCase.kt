package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode

abstract class ScalarColumnCase<T>(
  val name: String,
  val expectedValues: List<T>,
  val seed: () -> Unit,
  val query: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>
) {
  override fun toString() = name
}
