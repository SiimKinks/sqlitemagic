package com.siimkinks.sqlitemagic.sample.ui

import io.reactivex.Completable

internal fun interface OnWrite {
  fun write(operation: Completable, onSuccess: () -> Unit)

  operator fun invoke(
    operation: Completable,
    onSuccess: () -> Unit = {}
  ) = write(
    operation,
    onSuccess = onSuccess
  )
}
