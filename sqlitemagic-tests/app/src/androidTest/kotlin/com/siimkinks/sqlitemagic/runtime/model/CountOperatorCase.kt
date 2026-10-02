package com.siimkinks.sqlitemagic.runtime.model

enum class CountOperator {
  ZERO,
  NOT_ZERO
}

data class CountOperatorCase(
  val label: String,
  val operator: CountOperator,
  val emptyValue: Boolean,
  val nonEmptyValue: Boolean
) {
  override fun toString() = label
}
