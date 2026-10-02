package com.siimkinks.sqlitemagic.runtime.model

import io.reactivex.Maybe

data class CountQueryCase(
  val name: String,
  val seed: () -> Unit,
  val expectedCount: Long,
  val execute: () -> Long,
  val observeOnce: () -> Maybe<Long>
) {
  override fun toString() = name
}
