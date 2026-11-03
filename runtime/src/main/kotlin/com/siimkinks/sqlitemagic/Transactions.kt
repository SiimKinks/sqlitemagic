package com.siimkinks.sqlitemagic

inline fun inTransaction(crossinline block: () -> Unit) = SqliteMagic
  .getDefaultConnection()
  .inTransaction(block)

inline fun DbConnection.inTransaction(crossinline block: () -> Unit) {
  val transaction = newTransaction()
  try {
    block()
    transaction.markSuccessful()
  } catch (failure: Throwable) {
    try {
      transaction.end()
    } catch (endFailure: Throwable) {
      failure.addSuppressed(endFailure)
    }
    throw failure
  }
  transaction.end()
}
