package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.connection.ConnectionPool
import java.util.concurrent.locks.Condition

/**
 * Wait for the writer connection that a coroutine call suspends on instead of blocking its thread.
 *
 * A call run between [ReactiveAdapterSupport.beginWriterCall] and [ReactiveAdapterSupport.endWriterCall] throws it
 * before its first change when the writer is in use. Wait for the grant with [ReactiveAdapterSupport.awaitWriter] and
 * run the call again, passing this wait to [ReactiveAdapterSupport.beginWriterCall].
 */
@InternalSqliteMagicApi
class WriterWait internal constructor(
  internal val pool: ConnectionPool,
  internal val owner: LeaseOwner,
  internal val suspends: Boolean
) : RuntimeException(null, null, false, false) {
  internal var state = WAITING
  internal var parked = false
  internal var onGranted: (() -> Unit)? = null
  internal var granted: Condition? = null
}

/** Coroutine call that suspends instead of waiting for the writer connection, see [WriterWait]. */
@InternalSqliteMagicApi
class WriterCall internal constructor(
  internal val pool: ConnectionPool,
  internal val previous: WriterCall?,
  internal val grant: WriterWait?,
  internal val transactionOwner: LeaseOwner?,
  internal val yields: Int
) {
  internal var holdsWriter = false
}

internal const val WAITING = 0
internal const val GRANTED = 1
internal const val CLAIMED = 2
internal const val CANCELLED = 3
