package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteTransactionListener
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.internal.LogUtil.logInfo
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterSet
import java.util.concurrent.TimeUnit

/**
 * Note: some parts are forked from [sqlbrite](https://github.com/square/sqlbrite) and
 * modified to extend functionality and improvements.
 */
internal class DbConnectionImpl(
  private val database: GeneratedDatabase,
  private val dbHelper: SupportSQLiteOpenHelper,
  private val adapterSettings: ScatterMap<AdapterSettingKey<*>, Any>
) : DbConnection {
  private val transactions = ThreadLocal<SqliteTransaction?>()

  val tableObservers = TableObservers()

  private val transactionHandle = object : Transaction {
    override fun markSuccessful() {
      logDebug { "TXN SUCCESS ${transactions.get()}" }
      writableDatabase.setTransactionSuccessful()
    }

    override fun yieldIfContendedSafely() = writableDatabase.yieldIfContendedSafely()

    override fun yieldIfContendedSafely(
      sleepAmount: Long,
      sleepUnit: TimeUnit
    ) = writableDatabase.yieldIfContendedSafely(sleepUnit.toMillis(sleepAmount))

    override fun end() {
      val transactionState = checkNotNull(transactions.get()) { "Not in transaction." }
      transactions.set(transactionState.parent)
      logDebug { "TXN END $transactionState" }
      writableDatabase.endTransaction()
      if (transactionState.commit && transactionState.isNotEmpty()) {
        sendTableTriggers(transactionState)
      }
    }

    override fun close() = end()
  }

  private val submoduleEntityDbManagers = database
    .submoduleNames
    ?.let { submoduleNames ->
      MutableScatterMap<String, Array<EntityDbManager?>>(submoduleNames.size)
        .apply {
          for (submoduleName in submoduleNames) {
            put(
              key = submoduleName,
              value = createEntityDbManagers(submoduleName)
            )
          }
        }
    }
  private val entityDbManagers = createEntityDbManagers("")

  private fun createEntityDbManagers(moduleName: String) = Array<EntityDbManager?>(
    size = database.getNrOfTables(moduleName),
    init = { EntityDbManager(this) }
  )

  override fun close() {
    if (!tableObservers.close()) {
      return
    }
    closeEntityDbManagers(entityDbManagers)
    submoduleEntityDbManagers?.forEachValue(::closeEntityDbManagers)
    dbHelper.close()
    logInfo("Closed database [name=${dbHelper.databaseName}]")
  }

  private fun closeEntityDbManagers(managers: Array<EntityDbManager?>) {
    for (index in managers.indices) {
      managers[index]?.close()
      managers[index] = null
    }
  }

  @CheckResult
  override fun newTransaction(): Transaction {
    val transactionState = SqliteTransaction(transactions.get())
    transactions.set(transactionState)
    logDebug { "TXN BEGIN $transactionState" }
    writableDatabase.beginTransactionWithListener(transactionState)
    return transactionHandle
  }

  override fun clearData() {
    val cleared = database.clearData(writableDatabase)
    if (cleared.isNotEmpty()) {
      sendTableTriggers(cleared)
    }
  }

  val readableDatabase get() = dbHelper.readableDatabase

  val writableDatabase get() = dbHelper.writableDatabase

  val hasActiveTransaction get() = transactions.get() != null

  val isClosed get() = tableObservers.isClosed

  @Suppress("UNCHECKED_CAST")
  fun <V : Any> adapterSetting(key: AdapterSettingKey<V>) = adapterSettings[key] as V?

  fun ensureNotInTransaction() = check(!hasActiveTransaction) {
    "Cannot subscribe to observable query in a transaction."
  }

  @CheckResult
  fun getEntityDbManager(
    moduleName: String?,
    tablePos: Int
  ) = checkNotNull(moduleEntityDbManagers(moduleName)[tablePos]) { "DB connection closed" }

  private fun moduleEntityDbManagers(moduleName: String?) = when (moduleName) {
    null -> entityDbManagers
    else -> checkNotNull(submoduleEntityDbManagers?.get(moduleName)) {
      "Submodule $moduleName is not part of this database"
    }
  }

  fun compileStatement(sql: String) = writableDatabase.compileStatement(sql)

  fun sendTableTrigger(table: String) {
    when (val transactionState = transactions.get()) {
      null -> publishTableTrigger(table)
      else -> transactionState.add(table)
    }
  }

  fun sendTableTriggers(tables: Array<out String>) {
    when (tables.size) {
      0 -> Unit
      1 -> sendTableTrigger(tables[0])
      else -> when (val transactionState = transactions.get()) {
        null -> publishTableTriggers(
          MutableScatterSet<String>(initialCapacity = tables.size).apply { addAll(tables) }
        )
        else -> transactionState.addAll(tables)
      }
    }
  }

  private fun sendTableTriggers(tables: ScatterSet<String>) {
    when (val transactionState = transactions.get()) {
      null -> publishTableTriggers(tables)
      else -> transactionState.addAll(tables)
    }
  }

  private fun publishTableTrigger(table: String) {
    logDebug { "TRIGGER [$table]" }
    tableObservers.publish(table)
  }

  private fun publishTableTriggers(tables: ScatterSet<String>) {
    logDebug { "TRIGGER $tables" }
    tableObservers.publish(tables)
  }

  private class SqliteTransaction(
    val parent: SqliteTransaction?
  ) : MutableScatterSet<String>(initialCapacity = 0), SQLiteTransactionListener {
    var commit = false
      private set

    override fun onBegin() = Unit

    override fun onCommit() {
      commit = true
    }

    override fun onRollback() = Unit

    override fun toString(): String {
      val name = "%08x".format(System.identityHashCode(this))
      return when (parent) {
        null -> name
        else -> "$name [$parent]"
      }
    }
  }
}

internal fun DbConnectionImpl?.orDefault() = this ?: SqliteMagic.defaultDbConnection