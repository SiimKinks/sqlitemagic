package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode

internal fun <T> identity(value: T) = value

abstract class ScalarColumnCase<T>(
  val name: String,
  val expectedValues: List<T>,
  val seed: () -> Unit,
  val query: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>,
  private val normalize: (T) -> Any? = ::identity
) {
  fun comparableValue(value: T?): Any? = value?.let(normalize)

  fun comparableValues(values: Iterable<T>) = values.map(::comparableValue)

  override fun toString() = name
}
