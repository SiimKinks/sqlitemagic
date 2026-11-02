package com.siimkinks.sqlitemagic.connection

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.LeaseOwner
import com.siimkinks.sqlitemagic.internal.WriterWait
import com.siimkinks.sqlitemagic.sqlExceptionMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

internal class SharedLeaseOwnerTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `a shared transaction spans threads and leaves their own owners outside it`() {
    data class Outcome(
      val committed: Boolean,
      val inTransactionOutside: Boolean,
      val balanceReadOutside: Long,
      val balanceAfter: Long,
      val writerHeld: Boolean
    )

    val outcome = pool(fileName = folder.databasePath()).use { pool ->
      val owner = pool.shareOwner()
      pool.on(owner) { pool.beginTransaction() }
      pool.on(owner) {
        pool.setBalance(
          id = 1,
          balance = 0
        )
      }
      val inTransactionOutside = pool.inTransaction
      val balanceReadOutside = pool.balance(id = 1)
      val committed = pool.on(owner) {
        pool.setTransactionSuccessful()
        pool.endTransaction()
      }
      pool.releaseSharedOwner(owner)
      Outcome(
        committed = committed,
        inTransactionOutside = inTransactionOutside,
        balanceReadOutside = balanceReadOutside,
        balanceAfter = pool.balance(id = 1),
        writerHeld = pool.writerOwner != null
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        committed = true,
        inTransactionOutside = false,
        balanceReadOutside = 100,
        balanceAfter = 0,
        writerHeld = false
      )
    )
  }

  @Test
  fun `leases of a shared owner run one at a time`() {
    val outcome = pool(fileName = folder.databasePath()).use { pool ->
      val owner = pool.shareOwner()
      pool.on(owner) { pool.beginTransaction() }
      val active = AtomicInteger()
      val maxActive = AtomicInteger()
      val start = CountDownLatch(1)
      val workers = List(4) { worker ->
        thread {
          val previous = pool.installOwner(owner)
          try {
            start.await()
            repeat(20) {
              pool.write { pooled ->
                maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                Thread.sleep(1)
                pooled.connection.long("SELECT balance FROM account WHERE id = ${worker % 2 + 1}")
                active.decrementAndGet()
              }
              pool.balance(id = 1)
            }
          } finally {
            pool.restoreOwner(previous)
          }
        }
      }
      start.countDown()
      workers.forEach(Thread::join)
      pool.on(owner) { pool.endTransaction() }
      pool.releaseSharedOwner(owner)
      maxActive.get()
    }

    assertThat(outcome).isEqualTo(1)
  }

  @Test
  fun `a shared owner continues the transaction of the thread that asks for it and gives it back`() {
    data class Outcome(
      val inTransactionWhileShared: Boolean,
      val joinedWhileShared: Boolean,
      val nested: Boolean,
      val outer: Boolean,
      val balances: List<Long>
    )

    val outcomes = listOf(true, false).associateWith { nestedSuccessful ->
      pool(fileName = folder.databasePath("adopt-$nestedSuccessful.db")).use { pool ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 1
        )
        val owner = pool.shareOwner()
        val inTransactionWhileShared = pool.inTransaction
        val joinedWhileShared = pool.shareOwner() === owner
        pool.releaseSharedOwner(owner)
        val nested = pool.on(owner) {
          val level = checkNotNull(pool.beginTransaction())
          pool.setBalance(
            id = 2,
            balance = 2
          )
          if (nestedSuccessful) {
            pool.setNestedTransactionSuccessful(level)
          }
          pool.endNestedTransaction(level)
        }
        pool.releaseSharedOwner(owner)
        pool.setTransactionSuccessful()
        val outer = pool.endTransaction()
        Outcome(
          inTransactionWhileShared = inTransactionWhileShared,
          joinedWhileShared = joinedWhileShared,
          nested = nested,
          outer = outer,
          balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
        )
      }
    }

    assertThat(outcomes).containsExactly(
      true,
      Outcome(
        inTransactionWhileShared = true,
        joinedWhileShared = true,
        nested = true,
        outer = true,
        balances = listOf(1L, 2L)
      ),
      false,
      Outcome(
        inTransactionWhileShared = true,
        joinedWhileShared = true,
        nested = false,
        outer = false,
        balances = listOf(100L, 100L)
      )
    )
  }

  @Test
  fun `asking on a thread where a shared owner is installed joins it until every release`() {
    data class Outcome(
      val joined: Boolean,
      val usableAfterFirstRelease: Boolean,
      val failureAfterLastRelease: String?
    )

    val outcome = pool(fileName = folder.databasePath()).use { pool ->
      val owner = pool.shareOwner()
      val joined = pool.on(owner) { pool.shareOwner() } === owner
      pool.releaseSharedOwner(owner)
      val usable = runCatching { pool.on(owner) { pool.balance(id = 1) } }.isSuccess
      pool.releaseSharedOwner(owner)
      val failure = runCatching { pool.on(owner) { pool.balance(id = 1) } }.exceptionOrNull()
      Outcome(
        joined = joined,
        usableAfterFirstRelease = usable,
        failureAfterLastRelease = failure?.message
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        joined = true,
        usableAfterFirstRelease = true,
        failureAfterLastRelease = "Cannot use a coroutine transaction after it ended."
      )
    )
  }

  @Test
  fun `an ended shared owner rejects every lease`() {
    val cases = listOf<Pair<String, ConnectionPool.() -> Unit>>(
      "read" to { balance(id = 1) },
      "write" to {
        setBalance(
          id = 1,
          balance = 0
        )
      },
      "transaction" to { beginTransaction() }
    )

    val failures = pool(fileName = folder.databasePath()).use { pool ->
      val owner = pool.shareOwner()
      pool.releaseSharedOwner(owner)
      cases.map { (label, use) ->
        label to runCatching { pool.on(owner) { pool.use() } }.exceptionOrNull()?.message
      }
    }

    val ended = "Cannot use a coroutine transaction after it ended."
    assertThat(failures)
      .containsExactly(
        "read" to ended,
        "write" to ended,
        "transaction" to ended
      )
      .inOrder()
  }

  @Test
  fun `a shared transaction yields to a waiting writer and its next lease begins it again`() {
    data class Outcome(
      val uncontended: Boolean,
      val yielded: Boolean,
      val resumedByLease: Boolean,
      val committed: Boolean,
      val balances: List<Long>
    )

    val outcome = pool(fileName = folder.databasePath()).use { pool ->
      val owner = pool.shareOwner()
      pool.on(owner) {
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 1
        )
      }
      val uncontended = pool.yieldSharedTransaction(owner)
      val waiter = thread {
        pool.setBalance(
          id = 2,
          balance = 2
        )
      }
      waiter.awaitTimedWaiting()
      val yielded = pool.yieldSharedTransaction(owner)
      waiter.join()
      pool.on(owner) {
        pool.setBalance(
          id = 1,
          balance = 50
        )
      }
      val resumedByLease = pool.writerOwner === owner
      pool.resumeSharedTransaction(owner)
      val committed = pool.on(owner) { pool.endTransaction() }
      pool.releaseSharedOwner(owner)
      Outcome(
        uncontended = uncontended,
        yielded = yielded,
        resumedByLease = resumedByLease,
        committed = committed,
        balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        uncontended = false,
        yielded = true,
        resumedByLease = true,
        committed = true,
        balances = listOf(1L, 2L)
      )
    )
  }

  @Test
  fun `a nested or failed shared transaction does not yield`() {
    val cases = listOf<Pair<String, ConnectionPool.() -> Unit>>(
      "nested" to {
        beginTransaction()
        beginTransaction()
      },
      "after a failed nested level" to {
        beginTransaction()
        endNestedTransaction(checkNotNull(beginTransaction()))
      }
    )

    cases.forEachIndexed { index, (label, setUp) ->
      val yielded = pool(fileName = folder.databasePath("no-yield-$index.db")).use { pool ->
        val owner = pool.shareOwner()
        pool.on(owner) { pool.setUp() }
        val waiter = thread {
          pool.setBalance(
            id = 2,
            balance = 2
          )
        }
        waiter.awaitTimedWaiting()
        val yielded = pool.yieldSharedTransaction(owner)
        pool.on(owner) {
          while (pool.inTransaction) {
            pool.endTransaction()
          }
        }
        waiter.join()
        pool.releaseSharedOwner(owner)
        yielded
      }

      assertWithMessage(label)
        .that(yielded)
        .isFalse()
    }
  }

  @Test
  fun `a writer waiting for a shared transaction times out naming it`() {
    val message = pool(
      fileName = folder.databasePath(),
      poolTimeout = 200.milliseconds
    ).use { pool ->
      val owner = pool.shareOwner()
      pool.on(owner) { pool.beginTransaction() }
      val message = sqlExceptionMessage {
        pool.setBalance(
          id = 1,
          balance = 0
        )
      }
      pool.on(owner) { pool.endTransaction() }
      pool.releaseSharedOwner(owner)
      message
    }

    assertThat(message).isEqualTo(
      "Timed out after 200ms waiting for the writer connection, held by a coroutine transaction"
    )
  }

  @Test
  fun `a thread cannot use its transaction while a shared owner continues it`() {
    data class Outcome(
      val usesDuringLoan: List<String?>,
      val end: String?,
      val sharedCall: String?,
      val release: String?,
      val writerHeld: Boolean,
      val balances: List<Long>
    )

    val outcomes = listOf(false, true).associateWith { threadEndsDuringLoan ->
      pool(fileName = folder.databasePath("loan-$threadEndsDuringLoan.db")).use { pool ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 1
        )
        val owner = pool.shareOwner()
        val usesDuringLoan = listOf(
          runCatching {
            pool.setBalance(
              id = 1,
              balance = 5
            )
          },
          runCatching { pool.balance(id = 1) },
          runCatching { pool.setTransactionSuccessful() }
        ).map { it.exceptionOrNull()?.message }
        val end = when {
          threadEndsDuringLoan -> runCatching(pool::transactionToEnd).exceptionOrNull()?.message
          else -> null
        }
        val sharedCall = runCatching {
          pool.on(owner) {
            val level = checkNotNull(pool.beginTransaction())
            pool.setBalance(
              id = 2,
              balance = 2
            )
            pool.setNestedTransactionSuccessful(level)
            pool.endNestedTransaction(level)
          }
        }.exceptionOrNull()?.message
        val release = runCatching { pool.releaseSharedOwner(owner) }.exceptionOrNull()?.message
        if (!threadEndsDuringLoan) {
          pool.setTransactionSuccessful()
          pool.endTransaction()
        }
        Outcome(
          usesDuringLoan = usesDuringLoan,
          end = end,
          sharedCall = sharedCall,
          release = release,
          writerHeld = pool.writerOwner != null,
          balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
        )
      }
    }

    val lent = "Cannot use this transaction while a coroutine transaction started inside it runs. Wait for " +
        "withTransaction to return, as runBlocking does, before continuing the transaction."
    val abandoned = "The transaction that this coroutine transaction joined ended before it."
    assertThat(outcomes).containsExactly(
      false,
      Outcome(
        usesDuringLoan = listOf(lent, lent, lent),
        end = null,
        sharedCall = null,
        release = null,
        writerHeld = false,
        balances = listOf(1L, 2L)
      ),
      true,
      Outcome(
        usesDuringLoan = listOf(lent, lent, lent),
        end = lent,
        sharedCall = abandoned,
        release = abandoned,
        writerHeld = false,
        balances = listOf(100L, 100L)
      )
    )
  }

  @Test
  fun `a coroutine call waits for the writer in turn without blocking its thread`() {
    data class Outcome(
      val alreadyGranted: Boolean,
      val blockingWaiterQueuedAtGrant: Boolean,
      val writes: List<String>,
      val writerHeld: Boolean
    )

    val outcome = pool(fileName = folder.databasePath()).use { pool ->
      val owner = pool.shareOwner()
      pool.on(owner) { pool.beginTransaction() }
      val wait = pool
        .writerCall(grant = null) {
          pool.setBalance(
            id = 1,
            balance = 5
          )
        }
        .exceptionOrNull() as WriterWait
      val writes = Collections.synchronizedList(mutableListOf<String>())
      val blocking = thread {
        pool.setBalance(
          id = 2,
          balance = 6
        )
        writes += "blocking"
      }
      blocking.awaitTimedWaiting()
      val granted = CountDownLatch(1)
      val alreadyGranted = pool.awaitWriter(
        wait = wait,
        onGranted = granted::countDown
      )
      pool.on(owner) { pool.endTransaction() }
      granted.await()
      val blockingWaiterQueuedAtGrant = blocking.isAlive
      pool
        .writerCall(grant = wait) {
          pool.setBalance(
            id = 1,
            balance = 5
          )
          writes += "call"
        }
        .getOrThrow()
      blocking.join()
      pool.releaseSharedOwner(owner)
      Outcome(
        alreadyGranted = alreadyGranted,
        blockingWaiterQueuedAtGrant = blockingWaiterQueuedAtGrant,
        writes = writes.toList(),
        writerHeld = pool.writerOwner != null
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        alreadyGranted = false,
        blockingWaiterQueuedAtGrant = true,
        writes = listOf("call", "blocking"),
        writerHeld = false
      )
    )
  }

  @Test
  fun `a cancelled coroutine wait for the writer passes it on`() {
    data class Outcome(
      val blockingWaiterDone: Boolean,
      val writerHeld: Boolean,
      val balance: Long
    )

    val cancellations = listOf("while waiting", "after the grant")
    val outcomes = cancellations.associateWith { cancelled ->
      val index = cancellations.indexOf(cancelled)
      pool(fileName = folder.databasePath("cancel-$index.db")).use { pool ->
        val owner = pool.shareOwner()
        pool.on(owner) { pool.beginTransaction() }
        val wait = pool
          .writerCall(grant = null) {
            pool.setBalance(
              id = 1,
              balance = 5
            )
          }
          .exceptionOrNull() as WriterWait
        val blocking = thread {
          pool.setBalance(
            id = 2,
            balance = 6
          )
        }
        blocking.awaitTimedWaiting()
        if (cancelled == "while waiting") {
          pool.cancelWriterWait(wait)
        }
        pool.on(owner) { pool.endTransaction() }
        pool.cancelWriterWait(wait)
        blocking.join(5_000)
        pool.releaseSharedOwner(owner)
        Outcome(
          blockingWaiterDone = !blocking.isAlive,
          writerHeld = pool.writerOwner != null,
          balance = pool.balance(id = 2)
        )
      }
    }

    val passedOn = Outcome(
      blockingWaiterDone = true,
      writerHeld = false,
      balance = 6
    )
    assertThat(outcomes).containsExactly(
      "while waiting",
      passedOn,
      "after the grant",
      passedOn
    )
  }

  @Test
  fun `a coroutine call keeps the writer from its first write until it ends`() {
    data class Outcome(
      val heldInsideCall: Boolean,
      val heldAfterCall: Boolean
    )

    val outcome = pool(fileName = folder.databasePath()).use { pool ->
      val heldInside = pool
        .writerCall(grant = null) {
          pool.setBalance(
            id = 1,
            balance = 5
          )
          pool.writerOwner != null
        }
        .getOrThrow()
      Outcome(
        heldInsideCall = heldInside,
        heldAfterCall = pool.writerOwner != null
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        heldInsideCall = true,
        heldAfterCall = false
      )
    )
  }

  private fun <T> ConnectionPool.writerCall(grant: WriterWait?, block: () -> T): Result<T> {
    val result = AtomicReference<Result<T>>()
    thread {
      val call = beginWriterCall(grant)
      try {
        result.set(runCatching(block))
      } finally {
        endWriterCall(call)
      }
    }.join()
    return result.get()
  }

  private fun <T> ConnectionPool.on(owner: LeaseOwner, block: () -> T): T {
    val result = AtomicReference<Result<T>>()
    thread {
      val previous = installOwner(owner)
      try {
        result.set(runCatching(block))
      } finally {
        restoreOwner(previous)
      }
    }.join()
    return result
      .get()
      .getOrThrow()
  }
}
