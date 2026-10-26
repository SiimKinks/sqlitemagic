package com.siimkinks.sqlitemagic.runtime.support

import io.reactivex.Single

enum class OperationTerminal {
  EXECUTE,
  OBSERVE;

  fun <T> select(
    execute: () -> T,
    observe: () -> Single<T>
  ): T = when (this) {
    EXECUTE -> execute()
    OBSERVE -> observe().blockingGet()
  }
}
