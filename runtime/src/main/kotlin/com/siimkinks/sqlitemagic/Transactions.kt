package com.siimkinks.sqlitemagic

inline fun inTransaction(crossinline block: () -> Unit) {
  val transaction = SqliteMagic.newTransaction()
  try {
    block()
    transaction.markSuccessful()
  } finally {
    transaction.end()
  }
}

inline fun DbConnection.inTransaction(crossinline block: () -> Unit) {
  val transaction = newTransaction()
  try {
    block()
    transaction.markSuccessful()
  } finally {
    transaction.end()
  }
}
