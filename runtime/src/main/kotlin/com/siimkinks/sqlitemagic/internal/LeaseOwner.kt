package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.connection.ConnectionPool
import com.siimkinks.sqlitemagic.connection.ConnectionPool.TransactionLevel
import com.siimkinks.sqlitemagic.connection.PooledConnection
import java.util.concurrent.locks.ReentrantLock

/**
 * Holder of a connection's leases and transaction: a thread, or a coroutine transaction that runs on many threads.
 *
 * Get a coroutine transaction's owner from [ReactiveAdapterSupport.transactionOwner] and install it on every thread
 * that runs the transaction with [ReactiveAdapterSupport.installLeaseOwner].
 */
@InternalSqliteMagicApi
class LeaseOwner internal constructor(
  internal val thread: Thread?,
  internal val pool: ConnectionPool
) {
  internal var reader: PooledConnection? = null
  internal var wroteDuringRead = false
  internal var transaction: TransactionLevel? = null

  internal val leases = when (thread) {
    null -> ReentrantLock()
    else -> null
  }
  internal var joins = 0
  internal var nestedLevels = 0
  internal var adoptedFrom: LeaseOwner? = null
  internal var yieldedHolds = 0
  internal var yields = 0

  @Volatile
  internal var ended = false

  @Volatile
  internal var borrower: LeaseOwner? = null

  @Volatile
  internal var abandoned = false

  internal val inTransaction get() = transaction != null || borrower != null

  internal fun enterLease(): Boolean {
    val leases = leases ?: return false
    if (leases.isHeldByCurrentThread) {
      return false
    }
    leases.lock()
    return true
  }

  internal fun exitLease() {
    leases?.unlock()
  }

  override fun toString() = when (thread) {
    null -> "a coroutine transaction"
    else -> "thread ${thread.name}"
  }
}
