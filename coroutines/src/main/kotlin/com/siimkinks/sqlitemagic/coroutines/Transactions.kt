package com.siimkinks.sqlitemagic.coroutines

import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.Transaction
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.internal.LeaseOwner
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/** Scope of a database transaction started with [withTransaction]. */
sealed interface TransactionScope : CoroutineScope {
  /**
   * Temporarily end the transaction to let a waiting writer run. The work done so far is committed, a new transaction
   * begins when this returns, and the transaction continues as before.
   *
   * Nothing happens and this returns `false` when no other writer waits, when this transaction is nested in another
   * one, as a [withTransaction] called inside another transaction is, or after a nested transaction failed. A function
   * that yields therefore stays atomic when its caller runs it inside its own transaction.
   *
   * A call of this transaction that a child coroutine is running, such as a `useRows` block, finishes before the
   * transaction ends. A call made before it begins again, such as one from a child coroutine, begins it at once.
   *
   * @param sleep How long to wait before beginning again, allowing other writers to make more progress. The wait
   *     suspends without holding a thread.
   * @return Whether the transaction was committed and begun again
   */
  suspend fun yieldIfContended(sleep: Duration = Duration.ZERO): Boolean
}

/**
 * Run [block] in a transaction of the default connection and return its result.
 *
 * See [DbConnection.withTransaction].
 *
 * @param block Work to do in the transaction
 * @return Value returned by [block]
 */
suspend fun <T> withTransaction(block: suspend TransactionScope.() -> T): T = SqliteMagic
  .getDefaultConnection()
  .withTransaction(block)

/**
 * Run [block] in a transaction of this connection and return its result.
 *
 * The transaction commits when [block] and the coroutines it launched complete, and rolls back when they fail or are
 * cancelled. A cancellation that arrives after they complete, while the transaction commits, still makes this throw
 * [CancellationException] although the transaction committed. [block] runs on the [CoroutineDispatcher] supplied to
 * [dispatchCoroutineQueriesOn] and may suspend and switch dispatchers. Every call of this connection made by [block]
 * and its child coroutines takes part in the transaction: the suspending and blocking operations, one-shot queries
 * such as `runQueryOnce()`, which read the transaction's own changes, and nested [withTransaction] and [inTransaction]
 * calls, which become nested transactions. Collecting an `observe()` flow inside the transaction fails with
 * [IllegalStateException].
 *
 * Do not call `runBlocking` inside [block]. Its coroutine runs outside the transaction, so its calls wait for the
 * transaction to end and fail after the pool timeout. Given the context of [block], it waits for a thread of the
 * dispatcher that it blocks, and never returns when that dispatcher has no other thread. Code in [block] that cannot
 * suspend uses the blocking calls, such as `execute()`, which take part in the transaction.
 *
 * The transaction holds the writer connection from its beginning to its end, also while [block] suspends. Coroutine
 * calls outside it that need the writer, and the beginning of the transaction itself, suspend until the writer is free
 * instead of blocking a thread.
 *
 * Called inside a transaction that the calling thread started with [DbConnection.newTransaction], [block] runs as a
 * nested transaction of it. The thread cannot use its transaction until this returns, so wait for it, as `runBlocking`
 * does. Using the transaction before then fails with [IllegalStateException], and ending it rolls it back and fails
 * this call.
 *
 * Observers of the changed tables are notified after the transaction commits.
 *
 * @param block Work to do in the transaction
 * @return Value returned by [block]
 */
suspend fun <T> DbConnection.withTransaction(block: suspend TransactionScope.() -> T): T {
  val context = currentCoroutineContext()
  val owner = withOwnerOf(context) { ReactiveAdapterSupport.transactionOwner(this) }
  try {
    return withContext(
      queryDispatcherFor(context) + TransactionElement(
        connection = this,
        owner = owner
      )
    ) {
      val transaction = waitingForWriter(::newTransaction)
      val result = try {
        coroutineScope {
          block(
            TransactionScopeImpl(
              scope = this,
              connection = this@withTransaction,
              owner = owner
            )
          )
        }.also { transaction.markSuccessful() }
      } catch (failure: Throwable) {
        transaction.endAfter(failure)
      }
      transaction.end()
      result
    }
  } finally {
    ReactiveAdapterSupport.releaseTransactionOwner(owner)
  }
}

private fun Transaction.endAfter(failure: Throwable): Nothing {
  try {
    end()
  } catch (endFailure: Throwable) {
    failure.addSuppressed(endFailure)
  }
  throw failure
}

internal fun CoroutineContext.inTransactionOf(connection: DbConnection) =
  ReactiveAdapterSupport.installedTransactionOwner(connection) != null && transactionOwnerOf(connection) != null

internal inline fun <T> DbConnection.withOwnerOf(
  context: CoroutineContext,
  block: () -> T
): T {
  val installed = ReactiveAdapterSupport.installedTransactionOwner(this) ?: return block()
  val own = context.transactionOwnerOf(this)
  if (own === installed) {
    return block()
  }
  ReactiveAdapterSupport.restoreLeaseOwner(
    owner = installed,
    previous = own
  )
  try {
    return block()
  } finally {
    ReactiveAdapterSupport.restoreLeaseOwner(
      owner = installed,
      previous = installed
    )
  }
}

internal fun DbConnection.runsInsideOtherTransaction(context: CoroutineContext): Boolean {
  val installed = ReactiveAdapterSupport.installedTransactionOwner(this) ?: return false
  return context.transactionOwnerOf(this) !== installed
}

private fun CoroutineContext.transactionOwnerOf(connection: DbConnection) = fold<LeaseOwner?>(null) { found, element ->
  found ?: (element as? TransactionElement)
    ?.takeIf { it.connection === connection }
    ?.owner
}

internal class TransactionElement(
  val connection: DbConnection,
  val owner: LeaseOwner
) : ThreadContextElement<LeaseOwner?>, CoroutineContext.Key<TransactionElement> {
  override val key get() = this

  override fun updateThreadContext(context: CoroutineContext) = ReactiveAdapterSupport.installLeaseOwner(owner)

  override fun restoreThreadContext(context: CoroutineContext, oldState: LeaseOwner?) =
    ReactiveAdapterSupport.restoreLeaseOwner(
      owner = owner,
      previous = oldState
    )
}

private class TransactionScopeImpl(
  scope: CoroutineScope,
  private val connection: DbConnection,
  private val owner: LeaseOwner
) : TransactionScope, CoroutineScope by scope {
  override suspend fun yieldIfContended(sleep: Duration): Boolean {
    if (!ReactiveAdapterSupport.yieldTransaction(owner)) {
      return false
    }
    if (sleep.isPositive()) {
      delay(sleep)
    }
    connection.waitingForWriter { ReactiveAdapterSupport.resumeTransaction(owner) }
    return true
  }
}
