package com.siimkinks.sqlitemagic.coroutines

import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.closedConnection
import com.siimkinks.sqlitemagic.libraryDatabase
import com.siimkinks.sqlitemagic.testDbConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class FusedQueryOperatorsTest {
  private val executors = mutableListOf<ExecutorService>()
  private val queryDispatcher = executor("query-dispatcher").asCoroutineDispatcher()

  @After
  fun shutDownExecutors() = executors.forEach(ExecutorService::shutdownNow)

  @Test
  fun `operators applied directly to observe resume the collector once per result and query on the dispatcher`() {
    val collectorExecutor = executor("collector")
    val collector = CountingDispatcher(collectorExecutor.asCoroutineDispatcher())
    val outcomes = mapOf<String, Pair<CoroutineDispatcher, (DbConnectionImpl) -> Flow<*>>>(
      "directly on observe" to (queryDispatcher to { connection ->
        authorNames(connection)
          .observe()
          .runQuery()
      }),
      "after another operator" to (queryDispatcher to { connection ->
        authorNames(connection)
          .observe()
          .onEach {}
          .runQuery()
      }),
      "on the collector's dispatcher" to (collector to { connection ->
        authorNames(connection)
          .observe()
          .runQuery()
      })
    ).mapValues { (_, case) ->
      val (dispatcher, flow) = case
      val threads = CopyOnWriteArrayList<String>()
      val connection = queryingConnection(dispatcher = dispatcher) {
        threads += Thread
          .currentThread()
          .name
          .substringBefore(" @coroutine")
        mock<Cursor>()
      }
      val emissions = LinkedBlockingQueue<Int>()
      val collection = CoroutineScope(collector).launch {
        flow(connection).collect { emissions.add(collector.dispatches.get()) }
      }
      val dispatchCounts = List(4) { round ->
        if (round > 0) {
          collectorExecutor
            .submit {}
            .get()
          connection.sendTableTrigger("author")
        }
        checkNotNull(emissions.poll(5, SECONDS))
      }
      runBlocking { collection.cancelAndJoin() }
      dispatchCounts.zipWithNext { previous, next -> next - previous } to threads.distinct()
    }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "directly on observe" to (listOf(1, 1, 1) to listOf("query-dispatcher")),
        "after another operator" to (listOf(2, 2, 2) to listOf("query-dispatcher")),
        "on the collector's dispatcher" to (listOf(1, 1, 1) to listOf("collector"))
      )
    )
  }

  @Test
  fun `operators applied directly to observe emit the results of every query shape`() {
    val cases = mapOf<String, (DbConnectionImpl) -> Flow<Any?>>(
      "list" to { connection ->
        authorNames(connection)
          .observe()
          .runQuery()
      },
      "first row" to { connection ->
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .WHERE(AUTHOR.ID IS 1L)
          .usingConnection(connection)
          .takeFirst()
          .observe()
          .runQuery()
      },
      "first row or default" to { connection ->
        lovelaceNames(connection)
          .takeFirst()
          .observe()
          .runQueryOrDefault("none")
      },
      "count" to { connection ->
        lovelaceNames(connection)
          .count()
          .observe()
          .runQuery()
      },
      "count or default" to { connection ->
        lovelaceNames(connection)
          .count()
          .observe()
          .runQueryOrDefault(-1L)
      },
      "is zero" to { connection ->
        lovelaceNames(connection)
          .count()
          .observe()
          .isZero()
      },
      "is not zero" to { connection ->
        lovelaceNames(connection)
          .count()
          .observe()
          .isNotZero()
      },
      "cursor" to { connection ->
        lovelaceNames(connection)
          .toCursor()
          .observe()
          .runQuery()
          .map { it.use(Cursor::getCount) }
      }
    )

    val outcomes = cases.mapValues { (_, flow) ->
      libraryDatabase().use { database ->
        val connection = database.dbConnection(adapterSettings = dispatcherSettings(queryDispatcher))
        runBlocking {
          flow(connection)
            .withIndex()
            .onEach { (index) ->
              if (index == 0) {
                UPDATE
                  .RAW("UPDATE author SET name = ? WHERE id = ?", "Lovelace", 1L)
                  .notifies(AUTHOR)
                  .usingConnection(connection)
                  .execute()
              }
            }
            .take(2)
            .map { (_, value) -> value }
            .toList()
        }
      }
    }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "list" to listOf(listOf("Ada", "Grace", "Linus"), listOf("Lovelace", "Grace", "Linus")),
        "first row" to listOf("Ada", "Lovelace"),
        "first row or default" to listOf("none", "Lovelace"),
        "count" to listOf(0L, 1L),
        "count or default" to listOf(0L, 1L),
        "is zero" to listOf(true, false),
        "is not zero" to listOf(false, true),
        "cursor" to listOf(0, 1)
      )
    )
  }

  @Test
  fun `the next query starts while the collector processes a result and later changes collapse into one more query`() {
    val queries = AtomicInteger()
    val queried = Semaphore(0)
    val cursors = List(3) { index ->
      mock<Cursor>().also { cursor ->
        whenever(cursor.moveToNext()).thenReturn(true)
        whenever(cursor.getLong(0)).thenReturn(index + 1L)
      }
    }
    val connection = queryingConnection {
      cursors[queries.getAndIncrement()].also { queried.release() }
    }
    val processing = CountDownLatch(1)
    val release = CompletableDeferred<Unit>()
    val counts = CoroutineScope(executor("collector").asCoroutineDispatcher()).async {
      SELECT
        .FROM(AUTHOR)
        .usingConnection(connection)
        .count()
        .observe()
        .runQuery()
        .onEach { count ->
          if (count == 1L) {
            processing.countDown()
            release.await()
          }
        }
        .take(3)
        .toList()
    }
    check(processing.await(5, SECONDS))
    check(queried.tryAcquire(5, SECONDS))

    connection.sendTableTrigger("author")
    val queriedWhileProcessing = queried.tryAcquire(5, SECONDS)
    repeat(100) { connection.sendTableTrigger("author") }
    release.complete(Unit)

    assertThat(
      Triple(
        first = queriedWhileProcessing,
        second = runBlocking { withTimeout(5.seconds) { counts.await() } },
        third = queries.get()
      )
    )
      .isEqualTo(
        Triple(
          first = true,
          second = listOf(1L, 2L, 3L),
          third = 3
        )
      )
  }

  @Test
  fun `cancelling the collection closes a cursor waiting for the collector and leaves a delivered cursor open`() {
    val deliveredCursor = mock<Cursor>()
    val waitingCursor = mock<Cursor>()
    val queried = Semaphore(0)
    val connection = queryingConnection {
      queried.release()
      when (queried.availablePermits()) {
        1 -> deliveredCursor
        else -> waitingCursor
      }
    }
    val delivered = LinkedBlockingQueue<Cursor>()
    val collection = CoroutineScope(executor("collector").asCoroutineDispatcher()).launch {
      SELECT
        .FROM(AUTHOR)
        .usingConnection(connection)
        .toCursor()
        .observe()
        .runQuery()
        .collect { cursor ->
          delivered.add(cursor)
          awaitCancellation()
        }
    }
    val firstDelivery = delivered.poll(5, SECONDS)

    connection.sendTableTrigger("author")
    check(queried.tryAcquire(2, 5, SECONDS))
    runBlocking { collection.cancelAndJoin() }

    assertThat(firstDelivery to delivered.toList()).isEqualTo(deliveredCursor to emptyList<Cursor>())
    verify(waitingCursor).close()
    verifyNoMoreInteractions(waitingCursor)
    verifyNoInteractions(deliveredCursor)
  }

  @Test
  fun `cancelling the collection while a query runs delivers nothing, reads no row and closes a cursor result`() {
    val collector = executor("collector").asCoroutineDispatcher()
    val shapes = mapOf<String, (DbConnectionImpl) -> Flow<Any>>(
      "rows" to { connection ->
        authorNames(connection)
          .observe()
          .runQuery()
      },
      "cursor" to { connection ->
        SELECT
          .FROM(AUTHOR)
          .usingConnection(connection)
          .toCursor()
          .observe()
          .runQuery()
      }
    )
    val outcomes = mapOf(
      "on the collector's dispatcher" to collector,
      "on Unconfined" to Dispatchers.Unconfined,
      "on another dispatcher" to queryDispatcher
    ).flatMap { (dispatcherLabel, dispatcher) ->
      shapes.map { (shapeLabel, flow) ->
        val cursor = mock<Cursor>()
        lateinit var collection: Job
        val connection = queryingConnection(dispatcher = dispatcher) {
          collection.cancel()
          cursor
        }
        val delivered = mutableListOf<Any>()
        collection = CoroutineScope(collector).launch(start = LAZY) { flow(connection).collect(delivered::add) }
        collection.start()
        runBlocking { collection.join() }
        val cursorCalls = mockingDetails(cursor).invocations.map { it.method.name }
        "$shapeLabel $dispatcherLabel" to (delivered to cursorCalls)
      }
    }

    assertThat(outcomes.toMap()).isEqualTo(
      mapOf(
        "rows on the collector's dispatcher" to (emptyList<Any>() to listOf("getCount", "close")),
        "cursor on the collector's dispatcher" to (emptyList<Any>() to listOf("close")),
        "rows on Unconfined" to (emptyList<Any>() to listOf("getCount", "close")),
        "cursor on Unconfined" to (emptyList<Any>() to listOf("close")),
        "rows on another dispatcher" to (emptyList<Any>() to listOf("getCount", "close")),
        "cursor on another dispatcher" to (emptyList<Any>() to listOf("close"))
      )
    )
  }

  @Test
  fun `a collection that queries in its own context runs no further query once cancelled while processing a result`() {
    val collector = executor("collector").asCoroutineDispatcher()
    val outcomes = mapOf(
      "on the collector's dispatcher" to collector,
      "on Unconfined" to Dispatchers.Unconfined
    ).mapValues { (_, dispatcher) ->
      val queries = AtomicInteger()
      val connection = queryingConnection(dispatcher = dispatcher) {
        queries.incrementAndGet()
        mock<Cursor>()
      }
      val collection = CoroutineScope(collector).launch {
        authorNames(connection)
          .observe()
          .runQuery()
          .collect {
            connection.sendTableTrigger("author")
            currentCoroutineContext().cancel()
          }
      }
      runBlocking { collection.join() }
      queries.get()
    }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "on the collector's dispatcher" to 1,
        "on Unconfined" to 1
      )
    )
  }

  @Test
  fun `a failure to open or map a query fails the collection after closing the cursor`() {
    for ((label, case) in mapOf(
      "opening the cursor" to (IllegalStateException("cannot open the cursor") to null),
      "mapping a row" to (IllegalArgumentException("cannot map row") to mock<Cursor>()),
      "fatal mapping failure" to (LinkageError("fatal mapper failure") to mock<Cursor>())
    )) {
      val (failure, cursor) = case
      cursor?.let {
        whenever(it.count).thenReturn(1)
        whenever(it.moveToNext()).thenReturn(true)
        whenever(it.getString(0)).thenThrow(failure)
      }
      val connection = queryingConnection { cursor ?: throw failure }

      val thrown = assertThrows(Throwable::class.java) {
        runBlocking {
          authorNames(connection)
            .observe()
            .runQuery()
            .first()
        }
      }

      assertWithMessage(label)
        .that(thrown.javaClass to thrown.message)
        .isEqualTo(failure.javaClass to failure.message)
      cursor?.let { verify(it).close() }
    }
  }

  @Test
  fun `a failing collector fails the collection and stops the queries`() {
    val queries = AtomicInteger()
    val connection = queryingConnection {
      queries.incrementAndGet()
      mock<Cursor>()
    }
    val failure = IllegalStateException("collector failed")

    val thrown = assertThrows(IllegalStateException::class.java) {
      runBlocking {
        authorNames(connection)
          .observe()
          .runQuery()
          .collect { throw failure }
      }
    }
    connection.sendTableTrigger("author")

    assertThat(thrown.message to queries.get()).isEqualTo(failure.message to 1)
  }

  @Test
  fun `collecting on a thread with an active transaction fails before any query runs`() {
    val database = mock<SupportSQLiteDatabase>()
    val connection = testDbConnection(
      database = database,
      adapterSettings = dispatcherSettings(queryDispatcher)
    )
    val transaction = connection.newTransaction()
    val failure = try {
      assertThrows(IllegalStateException::class.java) {
        runBlocking {
          authorNames(connection)
            .observe()
            .runQuery()
            .first()
        }
      }
    } finally {
      transaction.end()
    }

    assertThat(failure.message).isEqualTo("Cannot subscribe to observable query in a transaction.")
    verify(database, never()).query(any<SupportSQLiteQuery>())
  }

  @Test
  fun `a closed connection, a source-free query and closing the connection complete the collection`() {
    val outcomes = mapOf<String, () -> List<*>>(
      "closed connection" to {
        runBlocking {
          authorNames(closedConnection(adapterSettings = dispatcherSettings(queryDispatcher)))
            .observe()
            .runQuery()
            .toList()
        }
      },
      "source-free query" to {
        libraryDatabase().use { database ->
          runBlocking {
            SELECT
              .COLUMN(8L.asValue())
              .usingConnection(database.dbConnection(adapterSettings = dispatcherSettings(queryDispatcher)))
              .observe()
              .runQuery()
              .toList()
          }
        }
      },
      "connection closed after the first result" to {
        val connection = queryingConnection { mock<Cursor>() }
        runBlocking {
          authorNames(connection)
            .observe()
            .runQuery()
            .onEach {
              mockStatic(Log::class.java).use {
                connection.close()
              }
            }
            .toList()
        }
      }
    ).mapValues { (_, collect) -> collect() }

    assertThat(outcomes).isEqualTo(
      mapOf(
        "closed connection" to emptyList<Any>(),
        "source-free query" to listOf(listOf(8L)),
        "connection closed after the first result" to listOf(emptyList<String>())
      )
    )
  }

  private fun executor(name: String) = Executors
    .newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }
    .also(executors::add)

  private fun queryingConnection(
    dispatcher: CoroutineDispatcher = queryDispatcher,
    onQuery: () -> Cursor
  ) = testDbConnection(
    database = mock<SupportSQLiteDatabase>().also { database ->
      whenever(database.query(any<SupportSQLiteQuery>())).thenAnswer { onQuery() }
    },
    adapterSettings = dispatcherSettings(dispatcher)
  )

  private fun authorNames(connection: DbConnectionImpl) = SELECT
    .COLUMN(AUTHOR.NAME)
    .FROM(AUTHOR)
    .ORDER_BY(AUTHOR.ID.ASC())
    .usingConnection(connection)

  private fun lovelaceNames(connection: DbConnectionImpl) = SELECT
    .COLUMN(AUTHOR.NAME)
    .FROM(AUTHOR)
    .WHERE(AUTHOR.NAME IS "Lovelace")
    .usingConnection(connection)

  private class CountingDispatcher(
    private val delegate: CoroutineDispatcher
  ) : CoroutineDispatcher() {
    val dispatches = AtomicInteger()

    override fun dispatch(
      context: CoroutineContext,
      block: Runnable
    ) {
      dispatches.incrementAndGet()
      delegate.dispatch(
        context = context,
        block = block
      )
    }
  }
}
