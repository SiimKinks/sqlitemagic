package com.siimkinks.sqlitemagic.internal

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// Like Kotlin's require() but without the .toString() call
@OptIn(ExperimentalContracts::class)
internal inline fun requirePrecondition(value: Boolean, lazyMessage: () -> String) {
  contract { returns() implies value }
  if (!value) {
    throw IllegalArgumentException(lazyMessage())
  }
}
