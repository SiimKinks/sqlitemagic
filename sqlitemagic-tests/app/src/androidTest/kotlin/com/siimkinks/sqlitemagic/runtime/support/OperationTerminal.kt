package com.siimkinks.sqlitemagic.runtime.support

enum class OperationTerminal {
  EXECUTE,
  OBSERVE;

  fun <T> select(
    execute: () -> T,
    observe: () -> ReactiveOperation<T>
  ): T = when (this) {
    EXECUTE -> execute()
    OBSERVE -> observe().await()
  }
}
