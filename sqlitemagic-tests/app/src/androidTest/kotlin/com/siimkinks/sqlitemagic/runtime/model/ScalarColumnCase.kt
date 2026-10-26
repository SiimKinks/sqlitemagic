package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.SelectQuery

internal fun <T> identity(value: T) = value

class ScalarColumnCase<T>(
  val name: String,
  val expectedValues: List<T>,
  val seed: () -> Unit,
  val query: () -> SelectQuery<out T, *>,
  private val normalize: (T) -> Any? = ::identity
) {
  fun comparableValue(value: T?) = value?.let(normalize)

  fun comparableValues(values: Iterable<T>) = values.map(::comparableValue)

  override fun toString() = name
}
