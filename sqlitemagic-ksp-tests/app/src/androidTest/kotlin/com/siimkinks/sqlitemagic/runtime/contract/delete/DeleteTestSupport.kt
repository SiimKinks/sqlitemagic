package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import io.reactivex.Single

internal fun <T> OperationTerminal.select(
  execute: () -> T,
  observe: () -> Single<T>
): T = when (this) {
  OperationTerminal.EXECUTE -> execute()
  OperationTerminal.OBSERVE -> observe().blockingGet()
}
