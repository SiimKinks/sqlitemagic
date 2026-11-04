package com.siimkinks.sqlitemagic

/**
 * Run [block] in a transaction on the default DB connection.
 *
 * @param block Database work to run in the transaction
 * @see DbConnection.inTransaction
 */
inline fun inTransaction(crossinline block: () -> Unit) = SqliteMagic
  .getDefaultConnection()
  .inTransaction(block)

/**
 * Run [block] in a transaction on this connection.
 *
 * The transaction commits when [block] returns, including through `return@inTransaction`, and rolls back when [block]
 * throws, rethrowing the exception. Inside another transaction of the calling thread it runs as a nested transaction,
 * which follows the rules of [DbConnection.newTransaction]: the work commits when the outermost transaction ends, and
 * a nested block that throws rolls the whole transaction back.
 *
 * The transaction belongs to the calling thread. [block] cannot suspend, and it cannot `return`, `break` or `continue`
 * out of the enclosing function. Observers of the changed tables are notified after the commit.
 *
 * ```kotlin
 * db.inTransaction {
 *   author.insert()
 *       .execute()
 *   book.insert()
 *       .execute()
 * }
 * ```
 *
 * @param block Database work to run in the transaction
 * @see DbConnection.newTransaction
 */
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
