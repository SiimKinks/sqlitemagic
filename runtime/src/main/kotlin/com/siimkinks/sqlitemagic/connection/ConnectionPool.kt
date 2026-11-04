package com.siimkinks.sqlitemagic.connection

import android.database.SQLException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.internal.CANCELLED
import com.siimkinks.sqlitemagic.internal.CLAIMED
import com.siimkinks.sqlitemagic.internal.GRANTED
import com.siimkinks.sqlitemagic.internal.LeaseOwner
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.ReadOnlyConnections
import com.siimkinks.sqlitemagic.internal.WAITING
import com.siimkinks.sqlitemagic.internal.WriterCall
import com.siimkinks.sqlitemagic.internal.WriterWait
import java.io.Closeable
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
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
  val poolTimeout get() = options.poolTimeout
  private val timeoutNanos = when {
    options.poolTimeout.isInfinite() -> Long.MAX_VALUE
    else -> options.poolTimeout.inWholeNanoseconds
  }

  private val writerQueue = ReentrantLock()
  private var writerLocked = false
  private val writerWaiters = ArrayDeque<WriterWait>()
  private var reserved: WriterWait? = null
  private var reservedAt = 0L
  private var writerRows = 0

  @Volatile
  private var queuedWriters = 0
  private val writerCalls = ThreadLocal<WriterCall?>()
  private val activeWriterCalls = AtomicInteger()

  @Volatile
  var writerOwner: LeaseOwner? = null
    private set
  private var writerHolds = 0
  private var writer: PooledConnection? = null

  @Volatile
  private var writerOpened = false

  private val readerLock = ReentrantLock()
  private val readerAvailable = readerLock.newCondition()
  private val idleReaders = ArrayDeque<PooledConnection>()
  private var openedReaders = 0

  private val owners = ThreadLocal<LeaseOwner?>()

  @Volatile
  var isClosed = false
    private set

  val inTransaction get() = owners.get()?.inTransaction == true

  val transaction get() = owners.get()?.transaction

  inline fun <T> read(
    requiresWriter: Boolean,
    rows: Boolean = false,
    block: (PooledConnection) -> T
  ): T {
    val owner = readerOwner(requiresWriter)
    val acquired = when (owner) {
      null -> writeLease()
      else -> readLease(owner)
    }
    val lease = when {
      rows -> rowsLease(acquired)
      else -> acquired
    }
    try {
      return block(
        leasedConnection(
          owner = owner,
          lease = lease
        )
      )
    } catch (failure: Throwable) {
      if (lease and USES_READER == 0) {
        detectRollback()
      }
      throw failure
    } finally {
      if (lease and RELEASES != 0) {
        releaseLease(
          owner = owner,
          lease = lease
        )
      }
    }
  }

  inline fun <T> write(block: (PooledConnection) -> T): T {
    val lease = writeLease()
    try {
      return block(heldWriter())
    } catch (failure: Throwable) {
      detectRollback()
      throw failure
    } finally {
      if (lease != 0) {
        releaseLease(
          owner = null,
          lease = lease
        )
      }
    }
  }

  fun readerOwner(requiresWriter: Boolean) = when {
    requiresWriter || maxReaders == 0 || writerOwner?.thread === Thread.currentThread() -> null
    else -> threadState()
  }

  fun readLease(owner: LeaseOwner): Int {
    val serialized = owner.enterLease()
    try {
      if (owner.yieldedHolds != 0) {
        resumeYielded(owner)
      }
      val lease = when {
        writerOwner === owner -> {
          checkOwnedWriter(owner)
          0
        }
        owner.reader == null -> {
          checkUsable(owner)
          owner.reader = acquireReader()
          USES_READER or HOLDS_READER
        }
        owner.wroteDuringRead -> {
          acquireWriter(owner)
          HOLDS_WRITER
        }
        else -> USES_READER
      }
      return when {
        serialized -> lease or HOLDS_SERIAL_LEASE
        else -> lease
      }
    } catch (failure: Throwable) {
      if (serialized) {
        owner.exitLease()
      }
      throw failure
    }
  }

  fun writeLease(): Int {
    val current = writerOwner
    if (current?.thread === Thread.currentThread()) {
      checkOwnedWriter(current)
      return 0
    }
    val owner = threadState()
    val serialized = owner.enterLease()
    try {
      if (owner.yieldedHolds != 0) {
        resumeYielded(owner)
      }
      val lease = when {
        writerOwner === owner -> {
          checkOwnedWriter(owner)
          0
        }
        else -> {
          acquireWriter(owner)
          HOLDS_WRITER
        }
      }
      return when {
        serialized -> lease or HOLDS_SERIAL_LEASE
        else -> lease
      }
    } catch (failure: Throwable) {
      if (serialized) {
        owner.exitLease()
      }
      throw failure
    }
  }

  fun rowsLease(lease: Int): Int {
    if (lease and USES_READER != 0) {
      return lease
    }
    writerRows++
    if (lease and HOLDS_WRITER == 0) {
      writerHolds++
    }
    return lease or HOLDS_WRITER or WRITER_ROWS
  }

  fun leasedConnection(owner: LeaseOwner?, lease: Int) = when {
    lease and USES_READER != 0 -> checkNotNull(owner?.reader)
    else -> heldWriter()
  }

  fun releaseLease(owner: LeaseOwner?, lease: Int) {
    val holder = when {
      lease and (HOLDS_READER or HOLDS_SERIAL_LEASE) != 0 -> owner ?: threadState()
      else -> null
    }
    try {
      if (lease and WRITER_ROWS != 0) {
        writerRows--
      }
      if (lease and HOLDS_WRITER != 0) {
        releaseWriter()
      }
      if (holder != null && lease and HOLDS_READER != 0) {
        val reader = checkNotNull(holder.reader)
        holder.reader = null
        holder.wroteDuringRead = false
        releaseReader(reader)
      }
    } finally {
      if (holder != null && lease and HOLDS_SERIAL_LEASE != 0) {
        holder.exitLease()
      }
    }
  }

  fun beginTransaction(): TransactionLevel? {
    val owner = threadState()
    return serialized(owner) {
      val current = owner.transaction
      if (current != null) {
        check(!current.detached) { DETACHED }
        check(!current.rolledBack) { ROLLED_BACK }
        check(!current.successful) { MARKED_SUCCESSFUL }
        val level = TransactionLevel(parent = current)
        if (owner.thread != null) {
          owner.transaction = level
          return null
        }
        owner.nestedLevels++
        return level
      }
      val connection = acquireWriter(owner)
      try {
        connection.execute("BEGIN IMMEDIATE")
      } catch (failure: Throwable) {
        releaseWriter()
        throw failure
      }
      owner.transaction = TransactionLevel(parent = null)
      null
    }
  }

  fun setTransactionSuccessful() {
    val owner = threadState()
    serialized(owner) {
      val level = currentTransaction(owner)
      check(!level.successful) { MARKED_SUCCESSFUL }
      level.successful = true
    }
  }

  fun setNestedTransactionSuccessful(level: TransactionLevel) {
    serialized(threadState()) {
      check(!level.ended) { NO_TRANSACTION }
      check(!level.successful) { MARKED_SUCCESSFUL }
      level.successful = true
    }
  }

  fun endNestedTransaction(level: TransactionLevel): Boolean {
    val owner = threadState()
    return serialized(owner) {
      check(!level.ended) { NO_TRANSACTION }
      level.ended = true
      owner.nestedLevels--
      if (!level.successful) {
        checkNotNull(level.parent).childFailed = true
      }
      level.successful
    }
  }

  fun endTransaction(): Boolean {
    val owner = threadState()
    return serialized(owner) {
      val level = currentTransaction(owner)
      val successful = level.successful && !level.childFailed && !level.rolledBack
      val parent = level.parent
      owner.transaction = parent
      if (parent == null && owner.thread == null) {
        owner.ended = true
        owners.set(null)
        failWriterWaits(owner)
      }
      when {
        parent != null -> {
          if (!successful) {
            parent.childFailed = true
          }
          successful
        }
        level.detached -> level.committedByYield
        owner.yieldedHolds != 0 -> {
          owner.yieldedHolds = 0
          releaseOwnedWriter(owner)
          level.committedByYield
        }
        else -> {
          try {
            endWriterTransaction { connection ->
              when {
                successful -> connection.commit()
                else -> connection.rollback()
              }
            }
          } finally {
            releaseWriter()
          }
          successful || level.committedByYield
        }
      }
    }
  }

  fun <T> savepoint(
    operation: () -> T,
    keep: (T) -> Boolean
  ): T {
    val owner = threadState()
    return serialized(owner) {
      val lease = writeLease()
      try {
        val connection = heldWriter()
        connection.execute(SAVEPOINT)
        val result = try {
          operation()
        } catch (failure: Throwable) {
          detectRollback()
          if (owner.transaction?.rolledBack != true) {
            rollbackSavepoint(
              owner = owner,
              connection = connection,
              failure = failure
            )
          }
          throw failure
        }
        when {
          keep(result) -> connection.execute(RELEASE_SAVEPOINT)
          else -> rollbackSavepoint(
            owner = owner,
            connection = connection,
            failure = null
          )
        }
        result
      } finally {
        if (lease != 0) {
          releaseLease(
            owner = null,
            lease = lease
          )
        }
      }
    }
  }

  private fun rollbackSavepoint(
    owner: LeaseOwner,
    connection: PooledConnection,
    failure: Throwable?
  ) {
    try {
      connection.execute(ROLLBACK_TO_SAVEPOINT)
      connection.execute(RELEASE_SAVEPOINT)
    } catch (rollbackFailure: Throwable) {
      owner.transaction?.childFailed = true
      when (failure) {
        null -> throw rollbackFailure
        else -> failure.addSuppressed(rollbackFailure)
      }
    }
  }

  inline fun queueChanges(add: (MutableScatterSet<String>) -> Unit): Boolean {
    val owner = owners.get() ?: return false
    return serialized(owner) {
      when (val level = owner.transaction) {
        null -> false
        else -> {
          add(level.changedTables)
          true
        }
      }
    }
  }

  fun yieldIfContended(sleepMillis: Long): Boolean {
    val owner = threadState()
    return serialized(owner) {
      val level = currentTransaction(owner)
      check(!level.successful) { MARKED_SUCCESSFUL }
      check(level.parent == null && owner.nestedLevels == 0) {
        "Cannot perform this operation because a nested transaction is in progress."
      }
      check(!level.detached) { DETACHED }
      check(!level.rolledBack) { ROLLED_BACK }
      if (level.childFailed || writerRows != 0 || !hasWriterWaiters()) {
        return false
      }
      val holds = yieldWriter(level)
      if (sleepMillis > 0) {
        sleepUninterruptibly(sleepMillis)
      }
      resumeWriter(
        owner = owner,
        level = level,
        holds = holds
      )
      true
    }
  }

  fun shareOwner(): LeaseOwner {
    val current = owners.get()
    val joined = when (current?.thread) {
      null -> current
      else -> current.borrower
    }
    if (joined != null) {
      serialized(joined) {
        checkUsable(joined)
        joined.joins++
      }
      return joined
    }
    val shared = LeaseOwner(
      thread = null,
      sharedPool = this
    )
    val adopted = current?.transaction ?: return shared
    shared.adoptedFrom = current
    shared.transaction = adopted
    current.transaction = null
    current.borrower = shared
    if (writerOwner === current) {
      writerOwner = shared
    }
    return shared
  }

  fun installOwner(owner: LeaseOwner): LeaseOwner? = owners
    .get()
    .also { owners.set(owner) }

  fun restoreOwner(previous: LeaseOwner?) = owners.set(previous)

  fun installedSharedOwner() = owners
    .get()
    ?.takeIf { it.thread == null }

  fun releaseSharedOwner(owner: LeaseOwner) {
    serialized(owner) {
      if (owner.joins > 0) {
        owner.joins--
        return
      }
      owner.ended = true
      failWriterWaits(owner)
      val adoptedFrom = owner.adoptedFrom
      if (adoptedFrom == null) {
        releaseOwnedWriter(owner)
        return
      }
      if (!owner.abandoned) {
        adoptedFrom.transaction = owner.transaction
        owner.transaction = null
        if (writerOwner === owner) {
          writerOwner = adoptedFrom
        }
      }
      adoptedFrom.borrower = null
      check(!owner.abandoned) { ABANDONED }
    }
  }

  fun yieldSharedTransaction(owner: LeaseOwner) = serialized(owner) {
    val level = owner.transaction
    when {
      level == null || level.parent != null || owner.nestedLevels > 0 || level.childFailed || level.detached ||
          level.rolledBack || owner.yieldedHolds != 0 || writerRows != 0 || !hasWriterWaiters() -> false
      else -> {
        owner.yieldedHolds = yieldWriter(level)
        owner.yields++
        true
      }
    }
  }

  fun resumeSharedTransaction(owner: LeaseOwner) = serialized(owner) {
    if (owner.yieldedHolds != 0) {
      resumeYielded(owner)
    }
  }

  inline fun <T> serialized(owner: LeaseOwner, block: () -> T): T {
    val entered = owner.enterLease()
    try {
      return block()
    } finally {
      if (entered) {
        owner.exitLease()
      }
    }
  }

  fun beginWriterCall(grant: WriterWait?): WriterCall {
    val transactionOwner = installedSharedOwner()
    return WriterCall(
      pool = this,
      previous = writerCalls.get(),
      grant = grant,
      transactionOwner = transactionOwner,
      yields = transactionOwner?.yields ?: 0
    ).also {
      writerCalls.set(it)
      activeWriterCalls.incrementAndGet()
    }
  }

  fun endWriterCall(call: WriterCall) {
    writerCalls.set(call.previous)
    activeWriterCalls.decrementAndGet()
    try {
      if (call.holdsWriter) {
        releaseWriter()
      }
    } finally {
      call.grant?.let(::cancelWriterWait)
    }
  }

  fun awaitWriter(wait: WriterWait, onGranted: () -> Unit) = writerQueue.withLock {
    when (wait.state) {
      WAITING -> {
        wait.onGranted = onGranted
        false
      }
      else -> true
    }
  }

  fun cancelWriterWait(wait: WriterWait) {
    val release = writerQueue.withLock {
      when (wait.state) {
        WAITING -> {
          removeWriterWait(wait)
          wait.state = CANCELLED
          false
        }
        GRANTED -> {
          wait.state = CANCELLED
          if (reserved === wait) {
            reserved = null
          }
          wait.parked
        }
        else -> false
      }
    }
    if (release) {
      unlockWriter()
    }
  }

  fun writerTimeout() = timeout(writerAwaited())

  override fun close() {
    writerQueue.withLock {
      if (isClosed) {
        return
      }
      isClosed = true
      if (!writerLocked) {
        closeWriter()
      }
    }
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
  }

  fun threadState() = owners.get() ?: LeaseOwner(
    thread = Thread.currentThread(),
    sharedPool = null
  ).also(owners::set)

  fun holdsWriter(): Boolean {
    val owner = writerOwner ?: return false
    return owner.thread === Thread.currentThread() || owners.get() === owner
  }

  private fun acquireWriter(owner: LeaseOwner): PooledConnection {
    checkOpen()
    when {
      writerOwner === owner -> writerHolds++
      else -> {
        checkUsable(owner)
        val call = when (activeWriterCalls.get()) {
          0 -> null
          else -> writerCalls.get()
        }
        lockWriter(
          owner = owner,
          call = call
        )
        writerOwner = owner
        writerHolds = 1
        if (call != null && owner.thread != null && !call.holdsWriter) {
          call.holdsWriter = true
          writerHolds = 2
        }
        if (owner.reader != null) {
          owner.wroteDuringRead = true
        }
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

  private fun releaseWriter() {
    if (writerHolds > 1) {
      writerHolds--
      return
    }
    writerHolds = 0
    writerOwner = null
    unlockWriter()
  }

  private fun checkUsable(owner: LeaseOwner) {
    check(!owner.ended) { "Cannot use a coroutine transaction after it ended." }
    check(!owner.abandoned) { ABANDONED }
    check(owner.borrower == null) { LENT }
    check(owner.transaction?.detached != true) { DETACHED }
  }

  private fun checkOwnedWriter(owner: LeaseOwner) {
    checkOpen()
    check(owner.transaction?.rolledBack != true) { ROLLED_BACK }
  }

  fun detectRollback() {
    val level = writerOwner?.transaction
    if (level == null || level.detached || level.rolledBack || writer?.connection?.inTransaction() != false) {
      return
    }
    generateSequence(
      seed = level,
      nextFunction = TransactionLevel::parent
    ).forEach { it.rolledBack = true }
  }

  private fun acquireReader(): PooledConnection {
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

  private fun releaseReader(reader: PooledConnection) {
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
    val connection = when (driver) {
      is ReadOnlyConnections -> driver.openReadOnly(fileName)
      else -> driver.open(fileName)
    }
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

  fun transactionToEnd(): TransactionLevel {
    val owner = threadState()
    owner.transaction?.let { return it }
    owner.borrower?.let(::abandonLoan)
    return currentTransaction(owner)
  }

  private fun currentTransaction(owner: LeaseOwner) = owner.transaction ?: run {
    check(owner.borrower == null) { LENT }
    checkNotNull(owner.transaction) { NO_TRANSACTION }
  }

  private fun abandonLoan(borrower: LeaseOwner): Nothing {
    serialized(borrower) {
      if (!borrower.abandoned) {
        borrower.abandoned = true
        borrower.transaction = null
        if (writerOwner === borrower) {
          try {
            endWriterTransaction(PooledConnection::rollback)
          } finally {
            releaseOwnedWriter(borrower)
          }
        }
      }
    }
    error(LENT)
  }

  private inline fun endWriterTransaction(end: (PooledConnection) -> Unit) {
    val connection = heldWriter()
    try {
      end(connection)
    } catch (failure: Throwable) {
      if (connection.connection.inTransaction()) {
        writer = null
        connection.closeQuietly(failure)
      }
      throw failure
    }
  }

  private fun releaseOwnedWriter(owner: LeaseOwner) {
    if (writerOwner === owner) {
      writerHolds = 1
      releaseWriter()
    }
  }

  private fun resumeYielded(owner: LeaseOwner) {
    val holds = owner.yieldedHolds
    owner.yieldedHolds = 0
    try {
      resumeWriter(
        owner = owner,
        level = currentTransaction(owner),
        holds = holds
      )
    } catch (busy: WriterWait) {
      owner.yieldedHolds = holds
      throw busy
    }
  }

  fun heldWriter() = checkNotNull(writer) { CLOSED }

  private fun closeWriter() {
    writer?.close()
    writer = null
  }

  fun checkOpen() = check(!isClosed) { CLOSED }

  private fun yieldWriter(level: TransactionLevel): Int {
    heldWriter().execute("COMMIT")
    level.committedByYield = true
    val holds = writerHolds
    writerHolds = 0
    writerOwner = null
    unlockWriter()
    return holds
  }

  private fun resumeWriter(
    owner: LeaseOwner,
    level: TransactionLevel,
    holds: Int
  ) {
    val reacquired = try {
      acquireWriter(owner)
    } catch (failure: Throwable) {
      if (failure !is WriterWait) {
        level.detached = true
      }
      throw failure
    }
    try {
      reacquired.execute("BEGIN IMMEDIATE")
    } catch (failure: Throwable) {
      level.detached = true
      releaseOwnedWriter(owner)
      throw failure
    }
    writerHolds = holds
  }

  private fun lockWriter(owner: LeaseOwner, call: WriterCall?) {
    var callbacks = emptyList<() -> Unit>()
    val wait = writerQueue.withLock {
      if (!writerLocked) {
        writerLocked = true
        return
      }
      val grant = call?.grant
      when {
        grant?.state == GRANTED && grant.parked -> {
          grant.state = CLAIMED
          reserved = null
          if (owner.thread == null) {
            callbacks = buildList {
              resolveOwnerWaits(
                owner = owner,
                state = GRANTED,
                callbacks = this
              )
              queuedWriters = writerWaiters.size
            }
          }
          null
        }
        grant?.state == WAITING -> {
          call.grant = null
          grant
        }
        else -> queueWriterWait(
          owner = owner,
          call = call
        )
      }
    }
    if (wait == null) {
      callbacks.forEach(Function0<Unit>::invoke)
      return
    }
    if (wait.suspends) {
      throw wait
    }
    awaitWriterGrant(wait)
  }

  private fun awaitWriterGrant(wait: WriterWait) {
    val deadline = System.nanoTime() + timeoutNanos
    var interrupted = false
    var callbacks = emptyList<() -> Unit>()
    writerQueue.lock()
    try {
      val granted = writerQueue.newCondition()
      wait.granted = granted
      while (wait.state != GRANTED) {
        val now = System.nanoTime()
        val takeIn = reservationDelay(
          wait = wait,
          now = now
        )
        if (takeIn == 0L) {
          callbacks = takeReservation(wait)
          break
        }
        val remaining = when (timeoutNanos) {
          Long.MAX_VALUE -> Long.MAX_VALUE
          else -> deadline - now
        }
        if (remaining <= 0) {
          removeWriterWait(wait)
          wait.state = CANCELLED
          if (reserved != null) {
            firstBlockingWait()
              ?.granted
              ?.signal()
          }
          throw timeout(writerAwaited())
        }
        val nanos = minOf(remaining, takeIn)
        if (nanos == Long.MAX_VALUE) {
          granted.awaitUninterruptibly()
          continue
        }
        try {
          granted.awaitNanos(nanos)
        } catch (_: InterruptedException) {
          interrupted = true
        }
      }
      wait.state = CLAIMED
    } finally {
      writerQueue.unlock()
      if (interrupted) {
        Thread.currentThread().interrupt()
      }
    }
    callbacks.forEach(Function0<Unit>::invoke)
  }

  private fun unlockWriter() {
    val callbacks = writerQueue.withLock {
      val next = writerWaiters.removeFirstOrNull()
      if (next == null) {
        writerLocked = false
        if (isClosed) {
          closeWriter()
        }
        return
      }
      val owner = next.owner
      next.parked = next.suspends
      writerOwner = when {
        next.parked -> null
        else -> owner
      }
      writerHolds = 0
      buildList {
        resolveWait(
          wait = next,
          state = GRANTED
        )?.let(::add)
        when {
          next.parked -> {
            reserved = next
            reservedAt = System.nanoTime()
            writerWaiters
              .firstOrNull { !it.suspends && it.owner === owner }
              ?.granted
              ?.signal()
            firstBlockingWait()
              ?.granted
              ?.signal()
          }
          owner.thread == null -> resolveOwnerWaits(
            owner = owner,
            state = GRANTED,
            callbacks = this
          )
        }
        queuedWriters = writerWaiters.size
      }
    }
    callbacks.forEach(Function0<Unit>::invoke)
  }

  private fun reservationDelay(wait: WriterWait, now: Long): Long {
    val taken = reserved ?: return Long.MAX_VALUE
    return when {
      taken.owner === wait.owner -> 0L
      firstBlockingWait() !== wait -> Long.MAX_VALUE
      else -> maxOf(0L, reservedAt + RESERVATION_GRACE_NANOS - now)
    }
  }

  private fun takeReservation(wait: WriterWait) = buildList {
    val taken = checkNotNull(reserved)
    reserved = null
    taken.state = WAITING
    taken.parked = false
    writerWaiters.remove(wait)
    writerWaiters.addFirst(taken)
    if (wait.owner.thread == null) {
      resolveOwnerWaits(
        owner = wait.owner,
        state = GRANTED,
        callbacks = this
      )
    }
    queuedWriters = writerWaiters.size
  }

  private fun firstBlockingWait() = writerWaiters.firstOrNull { !it.suspends }

  private fun failWriterWaits(owner: LeaseOwner) {
    val callbacks = writerQueue.withLock {
      buildList {
        resolveOwnerWaits(
          owner = owner,
          state = CANCELLED,
          callbacks = this
        )
        queuedWriters = writerWaiters.size
      }
    }
    callbacks.forEach(Function0<Unit>::invoke)
  }

  private fun resolveOwnerWaits(
    owner: LeaseOwner,
    state: Int,
    callbacks: MutableList<() -> Unit>
  ) {
    val waits = writerWaiters.iterator()
    while (waits.hasNext()) {
      val wait = waits.next()
      if (wait.owner === owner) {
        waits.remove()
        resolveWait(
          wait = wait,
          state = state
        )?.let(callbacks::add)
      }
    }
  }

  private fun resolveWait(wait: WriterWait, state: Int): (() -> Unit)? {
    wait.state = state
    wait.granted?.signal()
    return wait.onGranted.also { wait.onGranted = null }
  }

  fun hasWriterWaiters() = queuedWriters != 0

  private fun queueWriterWait(owner: LeaseOwner, call: WriterCall?): WriterWait {
    val suspends = call != null && when (owner.thread) {
      null -> call.transactionOwner === owner && call.yields == owner.yields
      else -> true
    }
    return WriterWait(
      pool = this,
      owner = owner,
      suspends = suspends
    ).also {
      writerWaiters.addLast(it)
      queuedWriters = writerWaiters.size
    }
  }

  private fun removeWriterWait(wait: WriterWait) {
    writerWaiters.remove(wait)
    queuedWriters = writerWaiters.size
  }

  private fun writerAwaited() = "the writer connection, held by ${writerOwner ?: "no thread"}"

  private fun timeout(awaited: String) = SQLException("Timed out after ${options.poolTimeout} waiting for $awaited")

  private fun AutoCloseable.closeQuietly(failure: Throwable) {
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

  internal class TransactionLevel(val parent: TransactionLevel?) {
    val changedTables = MutableScatterSet<String>(initialCapacity = 0)
    var successful = false
    var childFailed = false
    var committedByYield = false
    var detached = false
    var rolledBack = false
    var ended = false

    override fun toString(): String {
      val name = "%08x".format(System.identityHashCode(this))
      return when (parent) {
        null -> name
        else -> "$name [$parent]"
      }
    }
  }
}

private const val USES_READER = 1
private const val HOLDS_READER = 2
private const val HOLDS_WRITER = 4
private const val HOLDS_SERIAL_LEASE = 8
private const val WRITER_ROWS = 16
internal const val RELEASES = HOLDS_READER or HOLDS_WRITER or HOLDS_SERIAL_LEASE
private const val RESERVATION_GRACE_NANOS = 10_000_000L
private const val BUSY_TIMEOUT_MILLIS = 3000
private const val JOURNAL_SIZE_LIMIT = 524288
private const val SAVEPOINT = "SAVEPOINT sqlitemagic_operation"
private const val RELEASE_SAVEPOINT = "RELEASE sqlitemagic_operation"
private const val ROLLBACK_TO_SAVEPOINT = "ROLLBACK TO sqlitemagic_operation"
private const val CLOSED = "DB connection closed"
private const val NO_TRANSACTION = "Cannot perform this operation because there is no current transaction."
private const val LENT = "Cannot use this transaction while a coroutine transaction started inside it runs. Wait " +
    "for withTransaction to return, as runBlocking does, before continuing the transaction."
private const val DETACHED = "Cannot use this transaction because yieldIfContended could not resume it. End the " +
    "transaction; it keeps only the parts committed before the yield."
private const val ROLLED_BACK = "Cannot use this transaction because SQLite rolled it back when a statement " +
    "failed. End the transaction."
private const val ABANDONED = "The transaction that this coroutine transaction joined ended before it."
private const val MARKED_SUCCESSFUL = "Cannot perform this operation because the transaction has already been " +
    "marked successful. The only thing you can do now is call end()."
