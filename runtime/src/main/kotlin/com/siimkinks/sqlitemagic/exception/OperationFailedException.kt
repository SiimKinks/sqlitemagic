package com.siimkinks.sqlitemagic.exception

class OperationFailedException : RuntimeException {
  constructor(message: String) : super(message)

  constructor(
    message: String,
    cause: Throwable?
  ) : super(message, cause)
}
