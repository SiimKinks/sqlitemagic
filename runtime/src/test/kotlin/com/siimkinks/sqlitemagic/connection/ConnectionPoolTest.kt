package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.ReadOnlyConnections
import com.siimkinks.sqlitemagic.sqlExceptionMessage
import com.siimkinks.sqlitemagic.withWorkers
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

internal class ConnectionPoolTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `opening creates the schema once, then upgrades or downgrades by user_version`() {
    val path = folder.databasePath()
    val opens = listOf(1, 1, 3, 2).map { version ->
      val schema = RecordingSchema(version = version)
      pool(
        fileName = path,
        schema = schema
      ).use { pool ->
        val userVersion = pool.write { it.connection.long("PRAGMA user_version") }
        schema.events + "user_version=$userVersion"
      }
    }

    assertThat(opens)
      .containsExactly(
        listOf("configure", "create", "open", "user_version=1"),
        listOf("configure", "open", "user_version=1"),
        listOf("configure", "upgrade 1->3", "open", "user_version=3"),
        listOf("configure", "downgrade 3->2", "open", "user_version=2")
      )
      .inOrder()
  }

  @Test
  fun `opening migrates only when the version under the exclusive lock still differs`() {
    val path = folder.databasePath()
    pool(fileName = path).use { it.write {} }
    val bundled = BundledSQLiteDriver()
    val migratedMeanwhile = object : SQLiteDriver by bundled {
      override fun open(fileName: String): SQLiteConnection = bundled.open(fileName).let { connection ->
        object : SQLiteConnection by connection {
          override fun prepare(sql: String): SQLiteStatement {
            if (sql == "BEGIN EXCLUSIVE") {
              bundled
                .open(fileName)
                .use { it.execSQL("PRAGMA user_version = 2") }
            }
            return connection.prepare(sql)
          }
        }
      }
    }
    val schema = RecordingSchema(version = 2)

    val userVersion = pool(
      fileName = path,
      driver = migratedMeanwhile,
      schema = schema
    ).use { pool -> pool.write { it.connection.long("PRAGMA user_version") } }

    assertThat(schema.events)
      .containsExactly("configure", "open")
      .inOrder()
    assertThat(userVersion).isEqualTo(2L)
  }

  @Test
  fun `failed upgrade rolls back, keeps the old version and retries on the next access`() {
    val path = folder.databasePath()
    pool(fileName = path).use { it.write {} }
    val failing = RecordingSchema(
      version = 2,
      onUpgrade = { connection ->
        connection.execSQL("CREATE TABLE added(x)")
        error("upgrade failed")
      }
    )

    val failures = pool(
      fileName = path,
      schema = failing
    ).use { pool ->
      List(2) {
        assertThrows(IllegalStateException::class.java) { pool.write {} }.message
      }
    }
    val state = pool(fileName = path).use { pool ->
      pool.write { pooled ->
        listOf(
          pooled.connection.long("PRAGMA user_version"),
          pooled.connection.long("SELECT count(*) FROM sqlite_master WHERE name = 'added'")
        )
      }
    }

    assertThat(failures).containsExactly("upgrade failed", "upgrade failed")
    assertThat(failing.events)
      .containsExactly("configure", "upgrade 1->2", "configure", "upgrade 1->2")
      .inOrder()
    assertThat(state)
      .containsExactly(1L, 0L)
      .inOrder()
  }

  @Test
  fun `connections apply defaults, then the application hook, then the library invariants`() {
    val schema = RecordingSchema()
    val synchronousSeenByHook = mutableListOf<Long>()
    val pool = pool(
      fileName = folder.databasePath(),
      schema = schema,
      readerConnections = 1,
      configureConnection = { connection ->
        synchronousSeenByHook += connection.long("PRAGMA synchronous")
        connection.execSQL("PRAGMA synchronous = FULL")
        if (synchronousSeenByHook.size == 1) {
          connection.text("PRAGMA journal_mode = DELETE")
        }
      }
    )

    val settings = pool.use {
      listOf(
        pool.write { it.connection.settings() },
        pool.read(requiresWriter = false) { it.connection.settings() }
      )
    }

    assertThat(settings)
      .containsExactly(
        ConnectionSettings(
          busyTimeout = 3000,
          synchronous = 2,
          journalSizeLimit = 524288,
          journalMode = "wal",
          queryOnly = 0
        ),
        ConnectionSettings(
          busyTimeout = 3000,
          synchronous = 2,
          journalSizeLimit = 524288,
          journalMode = "wal",
          queryOnly = 1
        )
      )
      .inOrder()
    assertThat(synchronousSeenByHook).containsExactly(1L, 1L)
    assertThat(schema.events)
      .containsExactly("configure", "create", "open", "configure")
      .inOrder()
  }

  @Test
  fun `in-memory databases, zero readers and pooled drivers use one connection`() {
    data class Case(
      val label: String,
      val fileName: String,
      val readerConnections: Int = DEFAULT_READER_CONNECTIONS,
      val hasConnectionPool: Boolean = false
    )

    data class Observation(
      val maxReaders: Int,
      val readUsesWriter: Boolean,
      val journalMode: String,
      val synchronous: Long,
      val openedConnections: Int
    )

    val cases = listOf(
      Case(
        label = "in-memory",
        fileName = MEMORY_DATABASE
      ),
      Case(
        label = "zero readers",
        fileName = folder.databasePath("zero.db"),
        readerConnections = 0
      ),
      Case(
        label = "pooled driver",
        fileName = folder.databasePath("pooled.db"),
        hasConnectionPool = true
      )
    )

    for ((label, fileName, readerConnections, hasConnectionPool) in cases) {
      val driver = TrackingDriver(hasConnectionPool = hasConnectionPool)
      val observation = pool(
        fileName = fileName,
        driver = driver,
        readerConnections = readerConnections
      ).use { pool ->
        val writer = pool.write { it }
        Observation(
          maxReaders = pool.maxReaders,
          readUsesWriter = pool.read(requiresWriter = false) { it } === writer,
          journalMode = pool.write { it.connection.text("PRAGMA journal_mode") },
          synchronous = pool.write { it.connection.long("PRAGMA synchronous") },
          openedConnections = driver.connections.size
        )
      }

      assertWithMessage(label)
        .that(observation)
        .isEqualTo(
          Observation(
            maxReaders = 0,
            readUsesWriter = true,
            journalMode = when (fileName) {
              MEMORY_DATABASE -> "memory"
              else -> "delete"
            },
            synchronous = 2,
            openedConnections = 1
          )
        )
    }
  }

  @Test
  fun `readers open lazily up to the configured count and nested reads reuse the held reader`() {
    val driver = TrackingDriver()
    pool(
      fileName = folder.databasePath(),
      driver = driver,
      readerConnections = 2
    ).use { pool ->
      repeat(3) { pool.balance() }
      val afterSequentialReads = driver.connections.size
      val nestedReuse = pool.read(requiresWriter = false) { outer ->
        pool.read(requiresWriter = false) { inner -> inner === outer }
      }
      val holding = CountDownLatch(2)
      val release = CountDownLatch(1)
      val holders = List(2) {
        thread {
          pool.read(requiresWriter = false) {
            holding.countDown()
            release.await()
          }
        }
      }
      holding.await()
      val afterConcurrentReads = driver.connections.size
      val waiting = CountDownLatch(1)
      val waiter = thread {
        pool.balance()
        waiting.countDown()
      }
      val waitedForReader = !waiting.await(100, MILLISECONDS)
      release.countDown()
      waiter.join()
      holders.forEach(Thread::join)

      assertThat(
        listOf(
          afterSequentialReads,
          afterConcurrentReads,
          driver.connections.size
        )
      )
        .containsExactly(2, 3, 3)
        .inOrder()
      assertThat(nestedReuse).isTrue()
      assertThat(waitedForReader).isTrue()
    }
  }

  @Test
  fun `reads nested in a reader lease see the writes of their thread`() {
    pool(fileName = folder.databasePath()).use { pool ->
      val balances = pool.read(requiresWriter = false) { outer ->
        outer.connection
          .prepare("SELECT balance FROM account ORDER BY id")
          .use { rows ->
            rows.step()
            val beforeWrite = pool.balance()
            pool.setBalance(
              id = 1,
              balance = 0
            )
            listOf(beforeWrite, pool.balance())
          }
      }

      assertThat(balances)
        .containsExactly(100L, 0L)
        .inOrder()
    }
  }

  @Test
  fun `readers see the last committed state while the transaction thread sees its own writes`() {
    pool(fileName = folder.databasePath()).use { pool ->
      withWorkers { workers ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 0
        )
        val otherThread = workers
          .submit<Long> { pool.balance() }
          .get(5, SECONDS)
        val transactionThread = pool.balance()
        pool.setTransactionSuccessful()
        pool.endTransaction()
        val afterCommit = workers
          .submit<Long> { pool.balance() }
          .get(5, SECONDS)

        assertThat(listOf(otherThread, transactionThread, afterCommit))
          .containsExactly(100L, 0L, 0L)
          .inOrder()
      }
    }
  }

  @Test
  fun `temporary objects are read through the writer and readers reject writes`() {
    pool(fileName = folder.databasePath()).use { pool ->
      pool.write { it.connection.execSQL("CREATE TEMP TABLE scratch AS SELECT 1 AS x") }

      val throughWriter = pool.read(requiresWriter = true) { it.connection.long("SELECT count(*) FROM scratch") }
      val throughReader = assertThrows(SQLiteException::class.java) {
        pool.read(requiresWriter = false) { it.connection.long("SELECT count(*) FROM scratch") }
      }
      val writeThroughReader = assertThrows(SQLiteException::class.java) {
        pool.read(requiresWriter = false) { it.connection.execSQL("DELETE FROM account") }
      }

      assertThat(throughWriter).isEqualTo(1L)
      assertThat(throughReader)
        .hasMessageThat()
        .contains("no such table: scratch")
      assertThat(writeThroughReader)
        .hasMessageThat()
        .contains("attempt to write a readonly database")
    }
  }

  @Test
  fun `readers open read-only through a driver that has read-only connections`() {
    val bundled = BundledSQLiteDriver()
    val opened = mutableListOf<String>()
    val driver = object : SQLiteDriver by bundled, ReadOnlyConnections {
      override fun open(fileName: String): SQLiteConnection {
        opened += "open"
        return bundled.open(fileName)
      }

      override fun openReadOnly(fileName: String): SQLiteConnection {
        opened += "openReadOnly"
        return bundled.open(
          fileName = fileName,
          flags = SQLITE_OPEN_READONLY
        )
      }
    }

    val readerWrite = pool(
      fileName = folder.databasePath(),
      driver = driver,
      readerConnections = 1
    ).use { pool ->
      pool.read(requiresWriter = false) { pooled ->
        pooled.connection.execSQL("PRAGMA query_only = 0")
        runCatching { pooled.connection.execSQL("CREATE TABLE note(id INTEGER)") }
      }
    }

    assertThat(opened)
      .containsExactly("open", "openReadOnly")
      .inOrder()
    assertThat(readerWrite.exceptionOrNull())
      .hasMessageThat()
      .contains("attempt to write a readonly database")
  }

  @Test
  fun `waiting longer than the pool timeout fails and names what it waited for`() {
    pool(
      fileName = folder.databasePath(),
      readerConnections = 1,
      poolTimeout = 100.milliseconds
    ).use { pool ->
      pool.write {}
      val writerMessage = whileHeldBy(
        name = "writer holder",
        hold = { release ->
          pool.beginTransaction()
          release()
          pool.endTransaction()
        },
        attempt = { sqlExceptionMessage { pool.write {} } }
      )
      val readerMessage = whileHeldBy(
        name = "reader holder",
        hold = { release -> pool.read(requiresWriter = false) { release() } },
        attempt = { sqlExceptionMessage { pool.balance() } }
      )

      assertThat(listOf(writerMessage, readerMessage))
        .containsExactly(
          "Timed out after 100ms waiting for the writer connection, held by thread writer holder",
          "Timed out after 100ms waiting for a reader connection, all 1 are in use"
        )
        .inOrder()
    }
  }

  @Test
  fun `an interrupted waiter keeps waiting and keeps its interrupt status`() {
    pool(fileName = folder.databasePath()).use { pool ->
      pool.write {}
      val holding = CountDownLatch(1)
      val release = CountDownLatch(1)
      val holder = thread {
        pool.write {
          holding.countDown()
          release.await()
        }
      }
      holding.await()
      var outcome: Pair<Boolean, Boolean>? = null
      val waiter = thread {
        Thread.currentThread().interrupt()
        val wrote = pool.write { true }
        outcome = wrote to Thread.interrupted()
      }
      Thread.sleep(100)
      release.countDown()
      holder.join()
      waiter.join()

      assertThat(outcome).isEqualTo(true to true)
    }
  }

  @Test
  fun `blocked writers get the writer one at a time in the order they asked`() {
    pool(fileName = folder.databasePath()).use { pool ->
      pool.write {}
      val order = mutableListOf<String>()
      val waiters = whileHeldBy(
        name = "writer holder",
        hold = { release -> pool.write { release() } },
        attempt = {
          listOf("first", "second", "third").map { name ->
            thread(name = name) { pool.write { order.add(name) } }.also(Thread::awaitTimedWaiting)
          }
        }
      )
      waiters.forEach(Thread::join)

      assertThat(order)
        .containsExactly("first", "second", "third")
        .inOrder()
    }
  }

  @Test
  fun `close releases idle connections now and leased connections on release`() {
    val driver = TrackingDriver()
    val pool = pool(
      fileName = folder.databasePath(),
      driver = driver
    )
    val holding = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = thread {
      pool.read(requiresWriter = false) {
        holding.countDown()
        release.await()
      }
    }
    holding.await()
    pool.balance()

    pool.close()
    val afterClose = driver.connections.map(TrackingConnection::closed)
    release.countDown()
    holder.join()
    val afterRelease = driver.connections.map(TrackingConnection::closed)
    val reuse = assertThrows(IllegalStateException::class.java) { pool.write {} }

    assertThat(afterClose)
      .containsExactly(true, false, true)
      .inOrder()
    assertThat(afterRelease).containsExactly(true, true, true)
    assertThat(reuse)
      .hasMessageThat()
      .isEqualTo("DB connection closed")
    assertThat(pool.isClosed).isTrue()
  }

  @Test
  fun `a writer waiter released after close fails instead of reopening the database`() {
    val driver = TrackingDriver()
    val pool = pool(
      fileName = folder.databasePath(),
      driver = driver
    )
    val holding = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = thread {
      pool.write {
        holding.countDown()
        release.await()
      }
    }
    holding.await()
    var waiterFailure: Throwable? = null
    val waiter = thread {
      waiterFailure = runCatching { pool.write {} }.exceptionOrNull()
    }
    waiter.awaitTimedWaiting()
    pool.close()
    release.countDown()
    holder.join()
    waiter.join()

    assertThat(waiterFailure).isInstanceOf(IllegalStateException::class.java)
    assertThat(waiterFailure)
      .hasMessageThat()
      .isEqualTo("DB connection closed")
    assertThat(driver.connections.map(TrackingConnection::closed)).containsExactly(true)
  }

  @Test
  fun `close during the last release of the writer closes it`() {
    val driver = TrackingDriver()
    val pool = pool(
      fileName = folder.databasePath(),
      driver = driver
    )
    val holding = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = thread {
      pool.write {
        holding.countDown()
        release.await()
      }
    }
    holding.await()
    pool.beforeWriterQueueLock(
      thread = holder,
      skippedLocks = 0
    ) {
      thread(block = pool::close).join()
    }
    release.countDown()
    holder.join()

    assertThat(driver.connections.map(TrackingConnection::closed)).containsExactly(true)
  }

  @Test
  fun `checking for a transaction leaves no state on the thread`() {
    val previous = pool(fileName = folder.databasePath()).use { pool ->
      val checks = listOf(pool.inTransaction, pool.transaction)
      checks to pool.installOwner(pool.shareOwner())
    }

    assertThat(previous).isEqualTo(listOf(false, null) to null)
  }

  @Test
  fun `threads that used a closed pool do not retain it`() {
    val worker = Executors.newSingleThreadExecutor()
    try {
      val closedPool = closedPoolUsedOn(worker)
      var collections = 0
      while (closedPool.get() != null && collections < 100) {
        System.gc()
        collections++
      }

      assertThat(closedPool.get()).isNull()
    } finally {
      worker.shutdown()
    }
  }

  private fun closedPoolUsedOn(worker: ExecutorService): WeakReference<ConnectionPool> {
    val pool = pool(fileName = folder.databasePath())
    worker
      .submit {
        pool.balance()
        pool.setBalance(
          id = 1,
          balance = 5
        )
        pool.beginTransaction()
        pool.setTransactionSuccessful()
        pool.endTransaction()
      }
      .get()
    pool.close()
    return WeakReference(pool)
  }

  @Test
  fun `a reader that fails to open frees its slot`() {
    val driver = TrackingDriver()
    var configured = 0
    pool(
      fileName = folder.databasePath(),
      driver = driver,
      readerConnections = 1,
      poolTimeout = 100.milliseconds,
      configureConnection = {
        configured++
        check(configured != 2) { "reader configuration failed" }
      }
    ).use { pool ->
      val failure = assertThrows(IllegalStateException::class.java) { pool.balance() }
      val retried = pool.balance()

      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("reader configuration failed")
      assertThat(retried).isEqualTo(100L)
      assertThat(driver.connections.map(TrackingConnection::closed))
        .containsExactly(false, true, false)
        .inOrder()
    }
  }

  private data class ConnectionSettings(
    val busyTimeout: Long,
    val synchronous: Long,
    val journalSizeLimit: Long,
    val journalMode: String,
    val queryOnly: Long
  )

  private fun SQLiteConnection.settings() = ConnectionSettings(
    busyTimeout = long("PRAGMA busy_timeout"),
    synchronous = long("PRAGMA synchronous"),
    journalSizeLimit = long("PRAGMA journal_size_limit"),
    journalMode = text("PRAGMA journal_mode"),
    queryOnly = long("PRAGMA query_only")
  )
}
