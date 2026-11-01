package com.siimkinks.sqlitemagic.connection

import android.database.SQLException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import java.io.Closeable
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.getOrSet
import kotlin.concurrent.withLock

internal class ConnectionPool(
  private val driver: SQLiteDriver,
  private val fileName: String,
  private val options: ConnectionOptions,
  private val schema: SchemaCallback
) : Closeable {
  val maxReaders = when {
    fileName == MEMORY_DATABASE || driver.hasConnectionPool -> 0
    else -> options.readerConnections
  }
  private val timeoutNanos = when {
    options.poolTimeout.isInfinite() -> Long.MAX_VALUE
    else -> options.poolTimeout.inWholeNanoseconds
  }

  private val writerLock = ReentrantLock(true)

  @Volatile
  private var writerHolder: Thread? = null
  private var writer: PooledConnection? = null

  @Volatile
  private var writerOpened = false

  private val readerLock = ReentrantLock()
  private val readerAvailable = readerLock.newCondition()
  private val idleReaders = ArrayDeque<PooledConnection>()
  private var openedReaders = 0

  private val threadStates = ThreadLocal<ThreadState>()

  @Volatile
  var isClosed = false
    private set

  val inTransaction get() = transaction != null

  val transaction get() = threadState().transaction

  inline fun <T> read(requiresWriter: Boolean, block: (PooledConnection) -> T): T {
    if (requiresWriter || maxReaders == 0 || holdsWriter()) {
      return write(block)
    }
    val state = threadState()
    val held = state.reader
    return when {
      held == null -> {
        val reader = acquireReader()
        state.reader = reader
        try {
          block(reader)
        } finally {
          state.reader = null
          state.wroteDuringRead = false
          releaseReader(reader)
        }
      }
      state.wroteDuringRead -> write(block)
      else -> block(held)
    }
  }

  inline fun <T> write(block: (PooledConnection) -> T): T {
    val held = reentrantWriter()
    val connection = held ?: acquireWriter()
    try {
      return block(connection)
    } finally {
      if (held == null) {
        releaseWriter()
      }
    }
  }

  fun beginTransaction() {
    val state = threadState()
    val current = state.transaction
    if (current != null) {
      check(!current.successful) { MARKED_SUCCESSFUL }
      state.transaction = TransactionLevel(parent = current)
      return
    }
    val connection = acquireWriter()
    try {
      connection.execute("BEGIN IMMEDIATE")
    } catch (failure: Throwable) {
      releaseWriter()
      throw failure
    }
    state.transaction = TransactionLevel(parent = null)
  }

  fun setTransactionSuccessful() {
    val level = currentTransaction(threadState())
    check(!level.successful) { MARKED_SUCCESSFUL }
    level.successful = true
  }

  fun endTransaction(): Boolean {
    val state = threadState()
    val level = currentTransaction(state)
    val successful = level.successful && !level.childFailed
    val parent = level.parent
    state.transaction = parent
    if (parent != null) {
      if (!successful) {
        parent.childFailed = true
      }
      return successful
    }
    if (level.detached) {
      return level.committedByYield
    }
    val connection = heldWriter()
    try {
      when {
        successful -> connection.commit()
        else -> connection.rollback()
      }
    } finally {
      releaseWriter()
    }
    return successful || level.committedByYield
  }

  fun yieldIfContended(sleepMillis: Long): Boolean {
    val level = currentTransaction(threadState())
    check(!level.successful) { MARKED_SUCCESSFUL }
    check(level.parent == null) { "Cannot perform this operation because a nested transaction is in progress." }
    check(!level.detached) { NO_TRANSACTION }
    if (level.childFailed || !writerLock.hasQueuedThreads()) {
      return false
    }
    heldWriter().execute("COMMIT")
    level.committedByYield = true
    val holds = writerLock.holdCount
    writerHolder = null
    repeat(holds) { writerLock.unlock() }
    if (sleepMillis > 0) {
      sleepUninterruptibly(sleepMillis)
    }
    val reacquired = try {
      acquireWriter()
    } catch (failure: Throwable) {
      level.detached = true
      throw failure
    }
    repeat(holds - 1) { writerLock.lock() }
    reacquired.execute("BEGIN IMMEDIATE")
    return true
  }

  override fun close() {
    if (isClosed) {
      return
    }
    isClosed = true
    val idle = readerLock.withLock {
      idleReaders
        .toList()
        .also {
          openedReaders -= it.size
          idleReaders.clear()
          readerAvailable.signalAll()
        }
    }
    idle.forEach(PooledConnection::close)
    if (!writerLock.isHeldByCurrentThread && writerLock.tryLock()) {
      try {
        closeWriter()
      } finally {
        writerLock.unlock()
      }
    }
  }

  fun threadState() = threadStates.getOrSet(::ThreadState)

  fun holdsWriter() = writerLock.isHeldByCurrentThread

  fun reentrantWriter(): PooledConnection? {
    if (!writerLock.isHeldByCurrentThread) {
      return null
    }
    checkOpen()
    return writer
  }

  fun acquireWriter(): PooledConnection {
    checkOpen()
    lockWriter()
    if (writerLock.holdCount == 1) {
      writerHolder = Thread.currentThread()
      val state = threadStates.get()
      if (state?.reader != null) {
        state.wroteDuringRead = true
      }
    }
    return try {
      checkOpen()
      writer ?: openWriter()
    } catch (failure: Throwable) {
      releaseWriter()
      throw failure
    }
  }

  fun releaseWriter() {
    if (writerLock.holdCount == 1) {
      writerHolder = null
      if (isClosed) {
        closeWriter()
      }
    }
    writerLock.unlock()
  }

  fun acquireReader(): PooledConnection {
    if (!writerOpened) {
      write {}
    }
    val deadline = System.nanoTime() + timeoutNanos
    var interrupted = false
    readerLock.lock()
    try {
      while (true) {
        checkOpen()
        idleReaders
          .removeLastOrNull()
          ?.let { return it }
        if (openedReaders < maxReaders) {
          openedReaders++
          break
        }
        if (timeoutNanos == Long.MAX_VALUE) {
          readerAvailable.awaitUninterruptibly()
          continue
        }
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) {
          throw timeout("a reader connection, all $maxReaders are in use")
        }
        try {
          readerAvailable.awaitNanos(remaining)
        } catch (_: InterruptedException) {
          interrupted = true
        }
      }
    } finally {
      readerLock.unlock()
      if (interrupted) {
        Thread.currentThread().interrupt()
      }
    }
    return try {
      openReader()
    } catch (failure: Throwable) {
      readerLock.withLock {
        openedReaders--
        readerAvailable.signal()
      }
      throw failure
    }
  }

  fun releaseReader(reader: PooledConnection) {
    val closeReader = readerLock.withLock {
      when {
        isClosed -> {
          openedReaders--
          true
        }
        else -> {
          idleReaders.addLast(reader)
          readerAvailable.signal()
          false
        }
      }
    }
    if (closeReader) {
      reader.close()
    }
  }

  private fun openWriter(): PooledConnection {
    val connection = driver.open(fileName)
    try {
      configure(
        connection = connection,
        writer = true
      )
      migrate(connection)
      schema.open(connection)
    } catch (failure: Throwable) {
      connection.closeQuietly(failure)
      throw failure
    }
    return PooledConnection(
      connection = connection,
      driverPoolsConnections = driver.hasConnectionPool
    ).also {
      writer = it
      writerOpened = true
    }
  }

  private fun openReader(): PooledConnection {
    val connection = driver.open(fileName)
    try {
      configure(
        connection = connection,
        writer = false
      )
    } catch (failure: Throwable) {
      connection.closeQuietly(failure)
      throw failure
    }
    return PooledConnection(connection)
  }

  private fun configure(connection: SQLiteConnection, writer: Boolean) {
    connection.execSQL("PRAGMA busy_timeout = $BUSY_TIMEOUT_MILLIS")
    if (maxReaders > 0) {
      connection.execSQL("PRAGMA synchronous = NORMAL")
    }
    connection.execSQL("PRAGMA journal_size_limit = $JOURNAL_SIZE_LIMIT")
    options.configureConnection?.invoke(connection)
    when {
      !writer -> connection.execSQL("PRAGMA query_only = 1")
      maxReaders > 0 -> {
        val journalMode = connection.text("PRAGMA journal_mode = WAL")
        check(journalMode.equals("wal", ignoreCase = true)) {
          "$maxReaders reader connections need the WAL journal mode, but the database uses $journalMode. " +
              "Use 0 reader connections for this database."
        }
      }
    }
    schema.applySchemaPragmas(connection)
  }

  private fun migrate(connection: SQLiteConnection) {
    val newVersion = schema.version
    if (connection.userVersion() == newVersion) {
      return
    }
    connection.execSQL("BEGIN EXCLUSIVE")
    try {
      val oldVersion = connection.userVersion()
      if (oldVersion != newVersion) {
        when {
          oldVersion == 0 -> schema.create(connection)
          oldVersion > newVersion -> schema.downgrade(
            connection = connection,
            oldVersion = oldVersion,
            newVersion = newVersion
          )
          else -> schema.upgrade(
            connection = connection,
            oldVersion = oldVersion,
            newVersion = newVersion
          )
        }
        connection.execSQL("PRAGMA user_version = $newVersion")
      }
      connection.execSQL("COMMIT")
    } catch (failure: Throwable) {
      try {
        connection.execSQL("ROLLBACK")
      } catch (rollbackFailure: Throwable) {
        failure.addSuppressed(rollbackFailure)
      }
      throw failure
    }
  }

  fun currentTransaction() = currentTransaction(threadState())

  private fun currentTransaction(state: ThreadState) = checkNotNull(state.transaction) { NO_TRANSACTION }

  private fun heldWriter() = checkNotNull(writer) { CLOSED }

  private fun closeWriter() {
    writer?.close()
    writer = null
  }

  private fun checkOpen() = check(!isClosed) { CLOSED }

  private fun lockWriter() {
    if (timeoutNanos == Long.MAX_VALUE) {
      writerLock.lock()
      return
    }
    val deadline = System.nanoTime() + timeoutNanos
    var interrupted = false
    try {
      while (true) {
        try {
          if (writerLock.tryLock(deadline - System.nanoTime(), NANOSECONDS)) {
            return
          }
          throw timeout("the writer connection, held by ${writerHolder.describe()}")
        } catch (_: InterruptedException) {
          interrupted = true
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt()
      }
    }
  }

  private fun timeout(awaited: String) = SQLException("Timed out after ${options.poolTimeout} waiting for $awaited")

  private fun SQLiteConnection.closeQuietly(failure: Throwable) {
    try {
      close()
    } catch (closeFailure: Throwable) {
      failure.addSuppressed(closeFailure)
    }
  }

  private fun SQLiteConnection.text(sql: String) = prepare(sql).use { statement ->
    statement.step()
    statement.getText(0)
  }

  private fun SQLiteConnection.userVersion() = prepare("PRAGMA user_version").use { statement ->
    statement.step()
    statement.getLong(0).toInt()
  }

  private fun Thread?.describe() = when (this) {
    null -> "no thread"
    else -> "thread $name"
  }

  private fun sleepUninterruptibly(millis: Long) {
    val deadline = System.nanoTime() + MILLISECONDS.toNanos(millis)
    var interrupted = false
    while (true) {
      val remaining = deadline - System.nanoTime()
      if (remaining <= 0) {
        break
      }
      try {
        NANOSECONDS.sleep(remaining)
      } catch (_: InterruptedException) {
        interrupted = true
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt()
    }
  }

  internal class ThreadState {
    var reader: PooledConnection? = null
    var wroteDuringRead = false
    var transaction: TransactionLevel? = null
  }

  internal class TransactionLevel(val parent: TransactionLevel?) {
    val changedTables = MutableScatterSet<String>(initialCapacity = 0)
    var successful = false
    var childFailed = false
    var committedByYield = false
    var detached = false

    override fun toString(): String {
      val name = "%08x".format(System.identityHashCode(this))
      return when (parent) {
        null -> name
        else -> "$name [$parent]"
      }
    }
  }
}

private const val BUSY_TIMEOUT_MILLIS = 3000
private const val JOURNAL_SIZE_LIMIT = 524288
private const val CLOSED = "DB connection closed"
private const val NO_TRANSACTION = "Cannot perform this operation because there is no current transaction."
private const val MARKED_SUCCESSFUL = "Cannot perform this operation because the transaction has already been " +
    "marked successful. The only thing you can do now is call end()."
