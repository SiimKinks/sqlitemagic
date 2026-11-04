package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.sqlite.SQLiteDriver
import com.siimkinks.sqlitemagic.connection.ConnectionOptions
import com.siimkinks.sqlitemagic.connection.ConnectionPool
import com.siimkinks.sqlitemagic.connection.ConnectionPool.TransactionLevel
import com.siimkinks.sqlitemagic.connection.PooledConnection
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.LogUtil.logInfo
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterSet
import java.util.concurrent.TimeUnit

/**
 * Note: some parts are forked from [sqlbrite](https://github.com/square/sqlbrite) and
 * modified to extend functionality and improvements.
 */
internal class DbConnectionImpl(
  private val database: GeneratedDatabase,
  schema: DatabaseSchema,
  driver: SQLiteDriver,
  private val fileName: String,
  options: ConnectionOptions,
  private val adapterSettings: ScatterMap<AdapterSettingKey<*>, Any>
) : DbConnection {
  val pool = ConnectionPool(
    driver = driver,
    fileName = fileName,
    options = options,
    schema = schema
  )

  val tableObservers = TableObservers()

  private val transactionHandle = object : Transaction {
    override fun markSuccessful() {
      logDebug { "TXN SUCCESS ${pool.transaction}" }
      pool.setTransactionSuccessful()
    }

    override fun yieldIfContendedSafely() = pool.yieldIfContended(sleepMillis = 0)

    override fun yieldIfContendedSafely(
      sleepAmount: Long,
      sleepUnit: TimeUnit
    ) = pool.yieldIfContended(sleepMillis = sleepUnit.toMillis(sleepAmount))

    override fun end() {
      val transaction = pool.transactionToEnd()
      logDebug { "TXN END $transaction" }
      val committed = notifyingOnFailure(
        notify = {
          if (transaction.committedByYield) {
            sendChangedTables(transaction)
          }
        },
        block = pool::endTransaction
      )
      if (committed) {
        sendChangedTables(transaction)
      }
    }

    override fun close() = end()
  }

  private inner class NestedTransaction(private val level: TransactionLevel) : Transaction {
    override fun markSuccessful() {
      logDebug { "TXN SUCCESS $level" }
      pool.setNestedTransactionSuccessful(level)
    }

    override fun yieldIfContendedSafely() = pool.yieldIfContended(sleepMillis = 0)

    override fun yieldIfContendedSafely(
      sleepAmount: Long,
      sleepUnit: TimeUnit
    ) = pool.yieldIfContended(sleepMillis = sleepUnit.toMillis(sleepAmount))

    override fun end() {
      logDebug { "TXN END $level" }
      pool.endNestedTransaction(level)
    }

    override fun close() = end()
  }

  private val submoduleEntitySql = database.submoduleNames?.let(::createSubmoduleEntitySql)
  private val mainEntitySql = createEntitySql("")

  private fun createSubmoduleEntitySql(submoduleNames: Array<String>) =
    MutableScatterMap<String, Array<EntitySql>>(submoduleNames.size).apply {
      for (submoduleName in submoduleNames) {
        this[submoduleName] = createEntitySql(submoduleName)
      }
    }

  private fun createEntitySql(moduleName: String) = Array(
    size = database.getNrOfTables(moduleName),
    init = { EntitySql() }
  )

  override fun close() {
    if (!tableObservers.close()) {
      return
    }
    pool.close()
    logInfo("Closed database [name=$fileName]")
  }

  @CheckResult
  override fun newTransaction(): Transaction {
    val nested = pool.beginTransaction()
    logDebug { "TXN BEGIN ${nested ?: pool.transaction}" }
    return when (nested) {
      null -> transactionHandle
      else -> NestedTransaction(nested)
    }
  }

  fun <T> atomically(
    operation: () -> T,
    keep: (T) -> Boolean
  ) = when {
    pool.inTransaction -> pool.savepoint(
      operation = operation,
      keep = keep
    )
    else -> newTransaction().let { transaction ->
      try {
        operation().also { result ->
          if (keep(result)) {
            transaction.markSuccessful()
          }
        }
      } finally {
        transaction.end()
      }
    }
  }

  override fun clearData() = newTransaction().use { transaction ->
    sendTableTriggers(write { pooled -> database.clearData(pooled.connection) })
    transaction.markSuccessful()
  }

  val hasActiveTransaction get() = pool.inTransaction

  val isClosed get() = tableObservers.isClosed

  inline fun <T> read(
    requiresWriter: Boolean,
    rows: Boolean = false,
    block: (PooledConnection) -> T
  ) = pool.read(
    requiresWriter = requiresWriter,
    rows = rows,
    block = block
  )

  inline fun <T> write(block: (PooledConnection) -> T) = pool.write(block)

  @Suppress("UNCHECKED_CAST")
  fun <V : Any> adapterSetting(key: AdapterSettingKey<V>) = adapterSettings[key] as V?

  fun ensureNotInTransaction() = check(!hasActiveTransaction) {
    "Cannot subscribe to observable query in a transaction."
  }

  fun entitySql(
    moduleName: String?,
    tablePosition: Int
  ) = when (moduleName) {
    null -> mainEntitySql
    else -> checkNotNull(submoduleEntitySql?.get(moduleName)) {
      "Submodule $moduleName is not part of this database"
    }
  }[tablePosition]

  fun sendTableTrigger(table: String) {
    if (!pool.queueChanges { it.add(table) }) {
      logDebug { "TRIGGER [$table]" }
      tableObservers.publish(table)
    }
  }

  fun sendTableTriggers(tables: Array<out String>) {
    when (tables.size) {
      0 -> Unit
      1 -> sendTableTrigger(tables[0])
      else -> if (!pool.queueChanges { it.addAll(tables) }) {
        logDebug { "TRIGGER ${tables.contentToString()}" }
        tableObservers.publish(tables)
      }
    }
  }

  private fun sendChangedTables(transaction: TransactionLevel) {
    if (transaction.changedTables.isNotEmpty()) {
      sendTableTriggers(transaction.changedTables)
    }
  }

  private fun sendTableTriggers(tables: ScatterSet<String>) {
    if (!pool.queueChanges { it.addAll(tables) }) {
      logDebug { "TRIGGER $tables" }
      tableObservers.publish(tables)
    }
  }
}

internal fun DbConnectionImpl?.orDefault() = this ?: SqliteMagic.defaultDbConnection
