package com.siimkinks.sqlitemagic.coroutines

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.TestGeneratedDatabase
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import com.siimkinks.sqlitemagic.recordTriggers
import com.siimkinks.sqlitemagic.testDbConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.Thread.State.TIMED_WAITING
import java.lang.Thread.State.WAITING
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.milliseconds

internal class TransactionsTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val dispatcher = Executors
    .newFixedThreadPool(4)
    .asCoroutineDispatcher()
  private val connections = mutableListOf<DbConnectionImpl>()

  @After
  fun close() {
    connections.forEach { it.pool.close() }
    dispatcher.close()
  }

  @Test
  fun `calls on every thread of the transaction join it and observers hear of its changes after it commits`() {
    data class Outcome(
      val result: String,
      val agesInside: List<Int?>,
      val ageOutsideDuring: Int?,
      val triggersDuring: List<Set<String>>,
      val ageAfter: Int?,
      val triggersAfter: List<Set<String>>,
      val ranOnSeveralThreads: Boolean
    )

    val connection = personConnection()
    val recorder = connection.recordTriggers()
    val threads = ConcurrentHashMap.newKeySet<Thread>()
    var agesInside = emptyList<Int?>()
    var ageOutside: Int? = null
    var triggersDuring = emptyList<Set<String>>()
    val result = runBlocking {
      connection.withTransaction {
        threads += Thread.currentThread()
        connection
          .setAge(40)
          .await()
        val afterAwait = connection.age()
        withContext(Dispatchers.Default) {
          threads += Thread.currentThread()
          connection
            .setAge(41)
            .execute()
        }
        val afterHop = connection.age()
        delay(10.milliseconds)
        threads += Thread.currentThread()
        connection.inTransaction {
          connection
            .setAge(42)
            .execute()
        }
        agesInside = listOf(afterAwait, afterHop, connection.age())
        thread { ageOutside = connection.age() }.join()
        triggersDuring = recorder.triggers.toList()
        "done"
      }
    }

    assertThat(
      Outcome(
        result = result,
        agesInside = agesInside,
        ageOutsideDuring = ageOutside,
        triggersDuring = triggersDuring,
        ageAfter = connection.age(),
        triggersAfter = recorder.triggers,
        ranOnSeveralThreads = threads.size > 1
      )
    ).isEqualTo(
      Outcome(
        result = "done",
        agesInside = listOf(40, 41, 42),
        ageOutsideDuring = 36,
        triggersDuring = emptyList(),
        ageAfter = 42,
        triggersAfter = listOf(setOf("person")),
        ranOnSeveralThreads = true
      )
    )
  }

  @Test
  fun `a failure or cancellation of the block rolls the transaction back and frees the writer`() {
    data class Outcome(
      val failure: String?,
      val ageAfter: Int?,
      val triggers: List<Set<String>>,
      val ageAfterNextWrite: Int?
    )

    val cases = mapOf<String, suspend (DbConnectionImpl) -> Unit>(
      "the block throws" to { connection ->
        connection.withTransaction {
          connection
            .setAge(40)
            .await()
          error("boom")
        }
      },
      "a child coroutine fails" to { connection ->
        connection.withTransaction {
          launch(Dispatchers.Default) {
            connection
              .setAge(40)
              .await()
            error("child")
          }
        }
      },
      "the block is cancelled" to { connection ->
        runBlocking {
          val written = CompletableDeferred<Unit>()
          val job = launch {
            connection.withTransaction {
              connection
                .setAge(40)
                .await()
              written.complete(Unit)
              awaitCancellation()
            }
          }
          written.await()
          job.cancelAndJoin()
        }
      },
      "a nested transaction fails and the block catches it" to { connection ->
        connection.withTransaction {
          connection
            .setAge(40)
            .await()
          runCatching {
            connection.withTransaction {
              connection
                .setAge(41)
                .await()
              error("inner")
            }
          }
        }
      }
    )

    val outcomes = cases.mapValues { (_, run) ->
      val connection = personConnection()
      val recorder = connection.recordTriggers()
      val failure = runCatching { runBlocking { run(connection) } }.exceptionOrNull()
      val ageAfter = connection.age()
      connection
        .setAge(50)
        .execute()
      Outcome(
        failure = failure?.message,
        ageAfter = ageAfter,
        triggers = recorder.triggers.dropLast(1),
        ageAfterNextWrite = connection.age()
      )
    }

    val rolledBack = { failure: String? ->
      Outcome(
        failure = failure,
        ageAfter = 36,
        triggers = emptyList(),
        ageAfterNextWrite = 50
      )
    }
    assertThat(outcomes).isEqualTo(
      mapOf(
        "the block throws" to rolledBack("boom"),
        "a child coroutine fails" to rolledBack("child"),
        "the block is cancelled" to rolledBack(null),
        "a nested transaction fails and the block catches it" to rolledBack(null)
      )
    )
  }

  @Test
  fun `child coroutines on several threads share the transaction`() {
    val connection = personConnection()
    val recorder = connection.recordTriggers()

    runBlocking {
      connection.withTransaction {
        repeat(4) { worker ->
          launch(Dispatchers.Default) {
            repeat(25) { step ->
              val increment = UPDATE
                .RAW("UPDATE person SET age = age + 1 WHERE id = ?", worker % 2 + 1L)
                .notifies(PERSON)
                .usingConnection(connection)
              when {
                step % 2 == 0 -> increment.await()
                else -> increment.execute()
              }
            }
          }
        }
      }
    }

    assertThat(listOf(connection.age(id = 1L), connection.age(id = 2L), recorder.triggers))
      .containsExactly(36 + 50, 85 + 50, listOf(setOf("person")))
      .inOrder()
  }

  @Test
  fun `nested transactions of concurrent children belong to the child that began them`() {
    data class Outcome(
      val ages: List<Int?>,
      val triggers: List<Set<String>>
    )

    val outcomes = listOf(false, true).associateWith { nestedFails ->
      val connection = personConnection()
      val recorder = connection.recordTriggers()
      runBlocking {
        connection.withTransaction {
          repeat(4) { worker ->
            launch(Dispatchers.Default) {
              repeat(50) { step ->
                val increment = UPDATE
                  .RAW("UPDATE person SET age = age + 1 WHERE id = ?", worker % 2 + 1L)
                  .notifies(PERSON)
                  .usingConnection(connection)
                when {
                  step % 2 == 0 -> connection.inTransaction { increment.execute() }
                  else -> connection.withTransaction { increment.await() }
                }
              }
              if (nestedFails && worker == 0) {
                runCatching { connection.inTransaction { error("nested") } }
              }
            }
          }
        }
      }
      Outcome(
        ages = listOf(connection.age(id = 1L), connection.age(id = 2L)),
        triggers = recorder.triggers
      )
    }

    assertThat(outcomes).containsExactly(
      false,
      Outcome(
        ages = listOf(36 + 100, 85 + 100),
        triggers = listOf(setOf("person"))
      ),
      true,
      Outcome(
        ages = listOf(36, 85),
        triggers = emptyList()
      )
    )
  }

  @Test
  fun `one-shot queries read the transaction's changes and observing it is rejected`() {
    val connection = personConnection()
    val adaAge = SELECT
      .COLUMN(PERSON.AGE)
      .FROM(PERSON)
      .WHERE(PERSON.ID IS 1L)
      .usingConnection(connection)
    val query = runBlocking {
      adaAge
        .takeFirst()
        .observe()
        .first()
    }

    val outcomes = runBlocking {
      connection.withTransaction {
        connection
          .setAge(40)
          .await()
        mapOf(
          "list runQueryOnce" to adaAge
            .observe()
            .runQueryOnce(),
          "first-row runQueryOnce" to adaAge
            .takeFirst()
            .observe()
            .runQueryOnce(),
          "first-row runQueryOnceOrDefault" to adaAge
            .takeFirst()
            .observe()
            .runQueryOnceOrDefault(0),
          "count runQueryOnce" to SELECT
            .FROM(PERSON)
            .WHERE(PERSON.AGE IS 40)
            .usingConnection(connection)
            .count()
            .observe()
            .runQueryOnce(),
          "Query run" to query.run(),
          "raw await" to SELECT
            .RAW("SELECT age FROM person WHERE id = 1")
            .readAs(INT)
            .usingConnection(connection)
            .await(),
          "raw first-row await" to SELECT
            .RAW("SELECT age FROM person WHERE id = 1")
            .readAs(INT)
            .usingConnection(connection)
            .takeFirst()
            .await(),
          "collecting observe()" to runCatching {
            adaAge
              .takeFirst()
              .observe()
              .first()
          }.exceptionOrNull()?.message,
          "collecting runQuery()" to runCatching {
            adaAge
              .takeFirst()
              .observe()
              .runQuery()
              .first()
          }.exceptionOrNull()?.message
        )
      }
    }

    val rejected = "Cannot subscribe to observable query in a transaction."
    assertThat(outcomes).isEqualTo(
      mapOf(
        "list runQueryOnce" to listOf(40),
        "first-row runQueryOnce" to 40,
        "first-row runQueryOnceOrDefault" to 40,
        "count runQueryOnce" to 1L,
        "Query run" to 40,
        "raw await" to listOf(40),
        "raw first-row await" to 40,
        "collecting observe()" to rejected,
        "collecting runQuery()" to rejected
      )
    )
  }

  @Test
  fun `a coroutine that the transaction resumes without dispatching it runs outside the transaction`() {
    val connection = personConnection(queryDispatcher = Dispatchers.Unconfined)
    val adaAge = SELECT
      .COLUMN(PERSON.AGE)
      .FROM(PERSON)
      .WHERE(PERSON.ID IS 1L)
      .usingConnection(connection)
      .takeFirst()
    val resumed = CompletableDeferred<Unit>()
    val reads = CompletableDeferred<Map<String, Any?>>()
    val reader = CoroutineScope(Dispatchers.Unconfined).launch {
      resumed.await()
      reads.complete(
        mapOf<String, suspend () -> Any?>(
          "runQueryOnce" to {
            adaAge
              .observe()
              .runQueryOnce()
          },
          "collecting runQuery()" to {
            adaAge
              .observe()
              .runQuery()
              .first()
          },
          "Query run" to {
            adaAge
              .observe()
              .first()
              .run()
          },
          "raw await" to {
            SELECT
              .RAW("SELECT age FROM person WHERE id = 1")
              .readAs(INT)
              .usingConnection(connection)
              .takeFirst()
              .await()
          }
        ).mapValues { (_, read) -> runCatching { read() }.getOrElse(Throwable::toString) }
      )
    }

    val failure = runBlocking {
      runCatching {
        connection.withTransaction {
          connection
            .setAge(40)
            .await()
          withContext(Dispatchers.Default) { resumed.complete(Unit) }
          error("rolled back")
        }
      }.also { reader.join() }
    }.exceptionOrNull()

    assertThat(listOf(failure?.message, runBlocking { reads.await() }))
      .containsExactly(
        "rolled back",
        mapOf(
          "runQueryOnce" to 36,
          "collecting runQuery()" to 36,
          "Query run" to 36,
          "raw await" to 36
        )
      )
      .inOrder()
  }

  @Test
  fun `a coroutine started without dispatching on the query dispatcher inside a transaction runs outside it`() {
    val connection = personConnection(queryDispatcher = Dispatchers.IO)
    val outside = CompletableDeferred<List<Any?>>()
    val failure = runBlocking {
      runCatching {
        connection.withTransaction {
          connection
            .setAge(40)
            .await()
          CoroutineScope(Dispatchers.IO).launch(start = UNDISPATCHED) {
            outside.complete(
              runCatching {
                listOf(
                  connection
                    .setAge(
                      age = 50,
                      id = 2L
                    )
                    .await(),
                  SELECT
                    .COLUMN(PERSON.AGE)
                    .FROM(PERSON)
                    .WHERE(PERSON.ID IS 1L)
                    .usingConnection(connection)
                    .takeFirst()
                    .observe()
                    .runQueryOnce()
                )
              }.getOrElse { listOf(it.toString()) }
            )
          }
          error("rolled back")
        }
      }.exceptionOrNull()
    }

    assertThat(listOf(failure?.message, runBlocking { outside.await() }, connection.age(id = 2L)))
      .containsExactly("rolled back", listOf(1, 36), 50)
      .inOrder()
  }

  @Test(timeout = 10_000)
  fun `blocking calls of code that cannot suspend take part in the transaction on a single-thread dispatcher`() {
    val singleThread = Executors
      .newSingleThreadExecutor()
      .asCoroutineDispatcher()
    val connection = personConnection(queryDispatcher = singleThread)
    fun setAgeBlocking(age: Int) = connection
      .setAge(age)
      .execute()

    val ageInside = singleThread.use {
      runBlocking {
        connection.withTransaction {
          setAgeBlocking(40)
          connection.age()
        }
      }
    }

    assertThat(listOf(ageInside, connection.age()))
      .containsExactly(40, 40)
      .inOrder()
  }

  @Test
  fun `a coroutine transaction started inside a blocking transaction runs as its nested transaction`() {
    data class Outcome(
      val oneShotOutsideCoroutineTransaction: String?,
      val nestedFailure: String?,
      val ages: List<Int?>
    )

    val outcomes = listOf(true, false).associateWith { nestedSucceeds ->
      val connection = personConnection()
      var oneShot: String? = null
      var nestedFailure: String? = null
      connection.inTransaction {
        connection
          .setAge(40)
          .execute()
        oneShot = runCatching {
          runBlocking {
            SELECT
              .COLUMN(PERSON.AGE)
              .FROM(PERSON)
              .usingConnection(connection)
              .observe()
              .runQueryOnce()
          }
        }.exceptionOrNull()?.message
        nestedFailure = runCatching {
          runBlocking {
            connection.withTransaction {
              withContext(Dispatchers.Default) {
                connection
                  .setAge(
                    age = 90,
                    id = 2L
                  )
                  .await()
              }
              check(nestedSucceeds) { "nested" }
            }
          }
        }.exceptionOrNull()?.message
      }
      Outcome(
        oneShotOutsideCoroutineTransaction = oneShot,
        nestedFailure = nestedFailure,
        ages = listOf(connection.age(id = 1L), connection.age(id = 2L))
      )
    }

    val rejected = "Cannot execute observable query in a transaction."
    assertThat(outcomes).isEqualTo(
      mapOf(
        true to Outcome(
          oneShotOutsideCoroutineTransaction = rejected,
          nestedFailure = null,
          ages = listOf(40, 90)
        ),
        false to Outcome(
          oneShotOutsideCoroutineTransaction = rejected,
          nestedFailure = "nested",
          ages = listOf(36, 85)
        )
      )
    )
  }

  @Test
  fun `coroutine transactions started together inside a blocking transaction all join it`() {
    data class Outcome(
      val oneShotWhileJoined: String?,
      val failure: String?,
      val ages: List<Int?>
    )

    val outcomes = listOf(true, false).associateWith { secondSucceeds ->
      val connection = personConnection()
      var oneShot: String? = null
      val failure = runCatching {
        connection.inTransaction {
          connection
            .setAge(40)
            .execute()
          runBlocking {
            val secondWrote = CompletableDeferred<Unit>()
            launch {
              connection.withTransaction {
                secondWrote.await()
                connection
                  .setAge(41)
                  .await()
              }
            }
            launch {
              connection.withTransaction {
                connection
                  .setAge(
                    age = 90,
                    id = 2L
                  )
                  .await()
                secondWrote.complete(Unit)
                check(secondSucceeds) { "nested" }
              }
            }
            launch {
              oneShot = runCatching {
                SELECT
                  .COLUMN(PERSON.AGE)
                  .FROM(PERSON)
                  .usingConnection(connection)
                  .observe()
                  .runQueryOnce()
              }.exceptionOrNull()?.message
            }
          }
        }
      }.exceptionOrNull()?.message
      Outcome(
        oneShotWhileJoined = oneShot,
        failure = failure,
        ages = listOf(connection.age(id = 1L), connection.age(id = 2L))
      )
    }

    val rejected = "Cannot execute observable query in a transaction."
    assertThat(outcomes).isEqualTo(
      mapOf(
        true to Outcome(
          oneShotWhileJoined = rejected,
          failure = null,
          ages = listOf(41, 90)
        ),
        false to Outcome(
          oneShotWhileJoined = rejected,
          failure = "nested",
          ages = listOf(36, 85)
        )
      )
    )
  }

  @Test
  fun `a transaction yields only to a waiting writer and only at its top level`() {
    data class Outcome(
      val uncontended: Boolean,
      val contended: Boolean,
      val nested: Boolean,
      val failure: String?,
      val ages: List<Int?>
    )

    val connection = personConnection()
    var uncontended = true
    var contended = false
    var nested = true
    var nestedWaiter: Thread? = null
    val failure = runCatching {
      runBlocking {
        connection.withTransaction {
          uncontended = yieldIfContended()
          connection
            .setAge(40)
            .await()
          val waiterWrote = CompletableDeferred<Unit>()
          val child = launch(Dispatchers.Default) {
            waiterWrote.await()
            connection
              .setAge(
                age = 70,
                id = 4L
              )
              .execute()
          }
          val waiter = waitingWriter(
            connection = connection,
            id = 2L,
            age = 90
          ) { waiterWrote.complete(Unit) }
          contended = yieldIfContended(sleep = 300.milliseconds)
          waiter.join()
          child.join()
          connection
            .setAge(41)
            .await()
          nestedWaiter = waitingWriter(
            connection = connection,
            id = 3L,
            age = 99
          )
          nested = connection.withTransaction { yieldIfContended() }
          error("after yield")
        }
      }
    }.exceptionOrNull()
    nestedWaiter?.join()

    assertThat(
      Outcome(
        uncontended = uncontended,
        contended = contended,
        nested = nested,
        failure = failure?.message,
        ages = listOf(1L, 2L, 3L, 4L).map { connection.age(id = it) }
      )
    ).isEqualTo(
      Outcome(
        uncontended = false,
        contended = true,
        nested = false,
        failure = "after yield",
        ages = listOf(40, 90, 99, 17)
      )
    )
  }

  @Test(timeout = 30_000)
  fun `after SQLite rolls the transaction back a yield returns false and later calls fail`() {
    data class Outcome(
      val rollback: String?,
      val yielded: Boolean,
      val laterCall: String?,
      val failure: String?,
      val ages: List<Int?>
    )

    val connection = personConnection()
    var rollback: String? = null
    var yielded = true
    var laterCall: String? = null
    var waiter: Thread? = null
    val failure = runCatching {
      runBlocking {
        connection.withTransaction {
          connection
            .setAge(40)
            .await()
          rollback = runCatching {
            UPDATE
              .RAW("UPDATE OR ROLLBACK person SET id = 2 WHERE id = 1")
              .notifies(PERSON)
              .usingConnection(connection)
              .await()
          }.exceptionOrNull()?.message
          waiter = waitingWriter(
            connection = connection,
            id = 2L,
            age = 90
          )
          yielded = yieldIfContended()
          laterCall = runCatching {
            connection
              .setAge(41)
              .await()
          }.exceptionOrNull()?.message
        }
      }
    }.exceptionOrNull()
    waiter?.join()

    assertThat(
      Outcome(
        rollback = rollback,
        yielded = yielded,
        laterCall = laterCall,
        failure = failure?.message,
        ages = listOf(1L, 2L).map { connection.age(id = it) }
      )
    ).isEqualTo(
      Outcome(
        rollback = "Error code: 1555, message: UNIQUE constraint failed: person.id",
        yielded = false,
        laterCall = "Cannot use this transaction because SQLite rolled it back when a statement failed. End the " +
            "transaction.",
        failure = null,
        ages = listOf(36, 90)
      )
    )
  }

  @Test(timeout = 30_000)
  fun `a yield waits for the rows block of a child coroutine and then yields`() {
    data class Outcome(
      val yielded: Boolean,
      val writerWaitedForRows: Boolean,
      val rowsAges: List<Int>,
      val ages: List<Int?>
    )

    val connection = personConnection()
    val outcome = runBlocking {
      connection.withTransaction {
        connection
          .setAge(40)
          .await()
        val reading = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val reader = async(Dispatchers.Default) {
          SELECT
            .COLUMN(PERSON.AGE)
            .FROM(PERSON)
            .usingConnection(connection)
            .useRows { rows ->
              reading.complete(Unit)
              release.await()
              rows.toList()
            }
        }
        reading.await()
        val waiter = waitingWriter(
          connection = connection,
          id = 2L,
          age = 90
        )
        val yieldingThread = Thread.currentThread()
        var writerWaitedForRows = false
        val releaser = thread {
          while (yieldingThread.state != WAITING) {
            Thread.yield()
          }
          writerWaitedForRows = waiter.isAlive
          release.countDown()
        }
        val yielded = yieldIfContended()
        waiter.join()
        releaser.join()
        Outcome(
          yielded = yielded,
          writerWaitedForRows = writerWaitedForRows,
          rowsAges = reader.await(),
          ages = listOf(1L, 2L, 3L, 4L).map { connection.age(id = it) }
        )
      }
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        yielded = true,
        writerWaitedForRows = true,
        rowsAges = listOf(40, 85, 17, 17),
        ages = listOf(40, 90, 17, 17)
      )
    )
  }

  @Test(timeout = 30_000)
  fun `a call during the sleep of a yield begins the transaction again without holding a dispatcher thread`() {
    data class Outcome(
      val yielded: Boolean,
      val failure: String?,
      val ages: List<Int?>
    )

    val single = Executors
      .newSingleThreadExecutor()
      .asCoroutineDispatcher()
    val connection = personConnection(queryDispatcher = single)
    var yielded = false
    val failure = single.use {
      runCatching {
        runBlocking {
          connection.withTransaction {
            connection
              .setAge(40)
              .await()
            launch {
              connection
                .setAge(
                  age = 70,
                  id = 4L
                )
                .execute()
            }
            val waiter = waitingWriter(
              connection = connection,
              id = 2L,
              age = 90
            )
            yielded = yieldIfContended(sleep = 300.milliseconds)
            waiter.join()
            error("after yield")
          }
        }
      }.exceptionOrNull()
    }

    assertThat(
      Outcome(
        yielded = yielded,
        failure = failure?.message,
        ages = listOf(1L, 2L, 3L, 4L).map { connection.age(id = it) }
      )
    ).isEqualTo(
      Outcome(
        yielded = true,
        failure = "after yield",
        ages = listOf(40, 90, 17, 17)
      )
    )
  }

  @Test(timeout = 10_000)
  fun `coroutine calls wait for the writer of a suspended transaction without holding a dispatcher thread`() {
    data class Outcome(
      val yielded: Boolean,
      val ageSeenByWriter: Int?,
      val ages: List<Int?>
    )

    val singleThread = Executors
      .newSingleThreadExecutor()
      .asCoroutineDispatcher()
    val connection = personConnection(queryDispatcher = singleThread)
    val outcome = singleThread.use {
      runBlocking {
        val begun = CompletableDeferred<Unit>()
        val writerQueued = CompletableDeferred<Unit>()
        val transaction = async {
          connection.withTransaction {
            connection
              .setAge(40)
              .await()
            begun.complete(Unit)
            writerQueued.await()
            val yielded = yieldIfContended()
            connection
              .setAge(
                age = 41,
                id = 2L
              )
              .await()
            yielded
          }
        }
        begun.await()
        val writer = async {
          connection
            .setAge(
              age = 50,
              id = 3L
            )
            .await()
          connection.age(id = 1L)
        }
        while (!connection.pool.hasWriterWaiters()) {
          delay(1.milliseconds)
        }
        writerQueued.complete(Unit)
        Outcome(
          yielded = transaction.await(),
          ageSeenByWriter = writer.await(),
          ages = listOf(connection.age(id = 1L), connection.age(id = 2L), connection.age(id = 3L))
        )
      }
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        yielded = true,
        ageSeenByWriter = 40,
        ages = listOf(40, 41, 50)
      )
    )
  }

  @Test
  fun `the default connection runs withTransaction`() {
    val connection = personConnection()
    val previous = SqliteMagic.defaultConnection
    SqliteMagic.defaultConnection = connection
    try {
      runBlocking {
        withTransaction {
          connection
            .setAge(40)
            .await()
        }
      }
    } finally {
      SqliteMagic.defaultConnection = previous
    }

    assertThat(connection.age()).isEqualTo(40)
  }

  @Test
  fun `calls from a context that outlived its transaction fail`() {
    val connection = personConnection()
    val leaked = runBlocking {
      connection.withTransaction {
        coroutineContext
          .minusKey(Job)
          .minusKey(ContinuationInterceptor)
      }
    }

    val failure = runCatching {
      runBlocking(leaked) {
        connection
          .setAge(40)
          .execute()
      }
    }.exceptionOrNull()

    assertThat(listOf(failure?.message, connection.age()))
      .containsExactly("Cannot use a coroutine transaction after it ended.", 36)
      .inOrder()
  }

  @Test
  fun `observers that query on the thread that ends the transaction run outside it`() {
    data class Outcome(
      val failure: String?,
      val readsOnNotification: List<String>,
      val ageAfter: Int?
    )

    val outcomes = listOf("the transaction commits", "COMMIT fails after a yield").associateWith { case ->
      var failCommit = false
      val connection = personConnection(
        driver = driverRunningBeforeStep(steppedSql = "COMMIT") {
          if (failCommit) {
            failCommit = false
            error("COMMIT failed")
          }
        }
      )
      val reads = mutableListOf<String>()
      connection.tableObservers.add(
        tables = null,
        observer = object : TableChangeObserver {
          override fun onTablesChanged(tables: Set<String>) {
            reads += runCatching { connection.age().toString() }.getOrElse { it.message.toString() }
          }

          override fun onClosed() = Unit
        }
      )
      val failure = runCatching {
        runBlocking {
          connection.withTransaction {
            connection
              .setAge(40)
              .await()
            if (case == "COMMIT fails after a yield") {
              val waiter = waitingWriter(
                connection = connection,
                id = 2L,
                age = 90
              )
              yieldIfContended()
              waiter.join()
              connection
                .setAge(41)
                .await()
              failCommit = true
            }
          }
        }
      }.exceptionOrNull()
      Outcome(
        failure = failure?.message,
        readsOnNotification = reads,
        ageAfter = connection.age()
      )
    }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "the transaction commits" to Outcome(
          failure = null,
          readsOnNotification = listOf("40"),
          ageAfter = 40
        ),
        "COMMIT fails after a yield" to Outcome(
          failure = "COMMIT failed",
          readsOnNotification = listOf("40", "40"),
          ageAfter = 40
        )
      )
    )
  }

  @Test
  fun `closing the connection during a transaction fails its next call and rolls it back`() {
    val connection = personConnection()

    val failure = runCatching {
      runBlocking {
        connection.withTransaction {
          connection
            .setAge(40)
            .await()
          thread { connection.close() }.join()
          connection
            .setAge(41)
            .await()
        }
      }
    }.exceptionOrNull()

    assertThat(listOf(failure?.message, connection.pool.writerOwner))
      .containsExactly("DB connection closed", null)
      .inOrder()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `the failure of the block propagates and a failure to end the transaction is suppressed`() {
    class BlockFailure : IllegalStateException("boom"), CopyableThrowable<BlockFailure> {
      override fun createCopy() = null
    }

    data class Outcome(
      val failure: String?,
      val suppressed: List<String?>,
      val writerOwnerAfter: Any?,
      val nextTransaction: Any?
    )

    data class Case(
      val failingSql: String,
      val block: () -> Unit
    )

    val outcomes = mapOf(
      "the block fails and ROLLBACK fails" to Case(
        failingSql = "ROLLBACK",
        block = { throw BlockFailure() }
      ),
      "the block succeeds and COMMIT fails" to Case(
        failingSql = "COMMIT",
        block = {}
      )
    ).mapValues { (_, case) ->
      val connection = personConnection(
        driver = driverRunningBeforeStep(steppedSql = case.failingSql) { error("${case.failingSql} failed") }
      )
      val failure = runCatching { runBlocking { connection.withTransaction { case.block() } } }.exceptionOrNull()
      Outcome(
        failure = failure?.message,
        suppressed = failure
          ?.suppressed
          .orEmpty()
          .map(Throwable::message),
        writerOwnerAfter = connection.pool.writerOwner,
        nextTransaction = runCatching {
          runBlocking {
            connection.withTransaction {
              connection
                .setAge(41)
                .await()
              connection.age()
            }
          }
        }.getOrElse(Throwable::message)
      )
    }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "the block fails and ROLLBACK fails" to Outcome(
          failure = "boom",
          suppressed = listOf("ROLLBACK failed"),
          writerOwnerAfter = null,
          nextTransaction = 41
        ),
        "the block succeeds and COMMIT fails" to Outcome(
          failure = "COMMIT failed",
          suppressed = emptyList(),
          writerOwnerAfter = null,
          nextTransaction = "COMMIT failed"
        )
      )
    )
  }

  @Test
  fun `a cancellation after the block returns can arrive after the transaction committed`() {
    data class Outcome(
      val cancelled: Boolean,
      val ageAfter: Int?
    )

    val outcomes = listOf("before the block returns", "while the transaction commits").associateWith { case ->
      var caller: Job? = null
      var cancelOnCommit = false
      val connection = personConnection(
        driver = driverRunningBeforeStep(steppedSql = "COMMIT") {
          if (cancelOnCommit) {
            checkNotNull(caller).cancel()
          }
        }
      )
      var failure: Throwable? = null
      runBlocking {
        launch(Dispatchers.Default) {
          caller = currentCoroutineContext().job
          failure = runCatching {
            connection.withTransaction {
              connection
                .setAge(40)
                .await()
              when (case) {
                "before the block returns" -> checkNotNull(caller).cancel()
                else -> cancelOnCommit = true
              }
            }
          }.exceptionOrNull()
        }.join()
      }
      Outcome(
        cancelled = failure is CancellationException,
        ageAfter = connection.age()
      )
    }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "before the block returns" to Outcome(
          cancelled = true,
          ageAfter = 36
        ),
        "while the transaction commits" to Outcome(
          cancelled = true,
          ageAfter = 40
        )
      )
    )
  }

  private fun personConnection(
    queryDispatcher: CoroutineDispatcher = dispatcher,
    driver: SQLiteDriver = BundledSQLiteDriver()
  ) = testDbConnection(
    database = TestGeneratedDatabase(
      tableCount = 1,
      schema = listOf(
        "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT, age INTEGER NOT NULL, parent_id INTEGER, " +
            "token TEXT NOT NULL, flag INTEGER)",
        "INSERT INTO person VALUES (1, 'Ada', 36, NULL, 'db:a', 1), (2, 'Grace', 85, 1, 'db:g', 0), " +
            "(3, NULL, 17, 2, 'db:n', NULL), (4, 'Linus', 17, 4, 'db:l', 1)"
      )
    ),
    driver = driver,
    fileName = File(folder.root, "person-${connections.size}.db").path,
    adapterSettings = dispatcherSettings(queryDispatcher)
  ).also(connections::add)

  private fun driverRunningBeforeStep(
    steppedSql: String,
    beforeStep: () -> Unit
  ): SQLiteDriver {
    val driver = BundledSQLiteDriver()
    return object : SQLiteDriver by driver {
      override fun open(fileName: String): SQLiteConnection {
        val connection = driver.open(fileName)
        return object : SQLiteConnection by connection {
          override fun prepare(sql: String): SQLiteStatement {
            val statement = connection.prepare(sql)
            return when (sql) {
              steppedSql -> object : SQLiteStatement by statement {
                override fun step(): Boolean {
                  beforeStep()
                  return statement.step()
                }
              }
              else -> statement
            }
          }
        }
      }
    }
  }

  private fun DbConnectionImpl.age(id: Long = 1L) = SELECT
    .COLUMN(PERSON.AGE)
    .FROM(PERSON)
    .WHERE(PERSON.ID IS id)
    .usingConnection(this)
    .takeFirst()
    .execute()

  private fun DbConnectionImpl.setAge(age: Int, id: Long = 1L) = UPDATE
    .RAW("UPDATE person SET age = ? WHERE id = ?", age, id)
    .notifies(PERSON)
    .usingConnection(this)

  private fun waitingWriter(
    connection: DbConnectionImpl,
    id: Long,
    age: Int,
    onWritten: () -> Unit = {}
  ) = thread {
    connection
      .setAge(
        age = age,
        id = id
      )
      .execute()
    onWritten()
  }.also { waiter ->
    while (waiter.state != TIMED_WAITING) {
      Thread.yield()
    }
  }
}
