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
  @Suppress("UNCHECKED_CAST")
  fun comparableValue(value: Any?): Any? = when {
    value == null -> null
    else -> normalize(value as T)
  }

  fun comparableValues(values: Iterable<*>) = values.map(::comparableValue)

  override fun toString() = name
}
